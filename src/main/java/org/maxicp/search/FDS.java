/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 */

package org.maxicp.search;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.state.StateManager;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;
import java.util.function.Supplier;

/**
 * Failure-Directed Search (FDS) for scheduling and integer problems with CP variables.
 * <p>
 * This search algorithm is designed for a broad class of scheduling and constraint problems.
 * Instead of guiding the search towards possible solutions, FDS drives the search
 * into conflicts in order to prove that the current branch is infeasible.
 * Choices that fail the most are preferred (first-fail principle applied also to branch ordering).
 * <p>
 * The algorithm works with binary choices:
 * <ul>
 *   <li><b>Presence choice:</b> whether an optional interval variable is present or absent.</li>
 *   <li><b>Start time choice:</b> whether startOf(v) &le; t or startOf(v) &gt; t (domain splitting).</li>
 *   <li><b>Length choice:</b> whether length(v) &le; l or length(v) &gt; l (domain splitting).</li>
 *   <li><b>Integer choice:</b> whether x &le; v or x &gt; v (domain splitting).</li>
 * </ul>
 * <p>
 * The choices are generated lazily. At each branching point, the best unfixed
 * choice is selected based on FDS ratings. Ratings are updated with exponential decay
 * after each branch is explored, with emphasis on failures (localRating = 0 on failure).
 * <p>
 * Reference: Vilím, P., Laborie, P., Shaw, P. (2015).
 * Failure-directed Search for Constraint-based Scheduling.
 * CPAIOR 2015.
 *
 * @author pschaus
 */
public class FDS implements Supplier<Runnable[]> {

    // ------- Choice types -------

    /** Type of a binary choice. */
    private enum ChoiceType {
        PRESENCE, START, LENGTH, INT
    }

    /**
     * A binary choice that needs to be decided.
     * Each choice has a positive and negative branch with separate ratings.
     * <ul>
     *   <li>PRESENCE: positive = present, negative = absent</li>
     *   <li>START: positive = startOf(v) &le; splitValue, negative = startOf(v) &gt; splitValue</li>
     *   <li>LENGTH: positive = length(v) &le; splitValue, negative = length(v) &gt; splitValue</li>
     *   <li>INT: positive = x &le; splitValue, negative = x &gt; splitValue</li>
     * </ul>
     */
    private static final class Choice {
        final ChoiceType type;
        final int varIndex;
        final int splitValue; // used for START, LENGTH, and INT choices

        double ratingPos = 1.0; // rating for positive branch
        double ratingNeg = 1.0; // rating for negative branch

        Choice(ChoiceType type, int varIndex, int splitValue) {
            this.type = type;
            this.varIndex = varIndex;
            this.splitValue = splitValue;
        }

        /** Rating of the choice = sum of both branch ratings (lower is better). */
        double rating() {
            return ratingPos + ratingNeg;
        }

        /**
         * Check if this choice is resolved (already decided by propagation).
         */
        boolean isResolved(CPIntervalVar var) {
            return switch (type) {
                case PRESENCE -> var.isPresent() || var.isAbsent();
                case START -> {
                    if (var.isAbsent()) yield true;
                    if (var.isPresent()) {
                        yield var.startMax() <= splitValue || var.startMin() > splitValue;
                    }
                    yield false;
                }
                case LENGTH -> {
                    if (var.isAbsent()) yield true;
                    if (var.isPresent()) {
                        yield var.lengthMax() <= splitValue || var.lengthMin() > splitValue;
                    }
                    yield false;
                }
                case INT -> false;
            };
        }

        /**
         * Check if this choice is waiting (cannot be applied yet).
         * Start and length choices wait until the variable is present.
         */
        boolean isWaiting(CPIntervalVar var) {
            return switch (type) {
                case PRESENCE -> false;
                case START, LENGTH -> var.isOptional();
                case INT -> false;
            };
        }

        /**
         * Apply the positive branch.
         */
        void applyPositive(CPIntervalVar var, CPSolver cp) {
            switch (type) {
                case PRESENCE -> var.setPresent();
                case START -> var.setStartMax(splitValue);
                case LENGTH -> var.setLengthMax(splitValue);
                case INT -> throw new IllegalStateException("INT choice must be applied with CPIntVar");
            }
            cp.fixPoint();
        }

        /**
         * Apply the negative branch.
         */
        void applyNegative(CPIntervalVar var, CPSolver cp) {
            switch (type) {
                case PRESENCE -> var.setAbsent();
                case START -> var.setStartMin(splitValue + 1);
                case LENGTH -> var.setLengthMin(splitValue + 1);
                case INT -> throw new IllegalStateException("INT choice must be applied with CPIntVar");
            }
            cp.fixPoint();
        }

        @Override
        public String toString() {
            return switch (type) {
                case PRESENCE -> "Presence(var=" + varIndex + ")";
                case START -> "Start(var=" + varIndex + ",t=" + splitValue + ")";
                case LENGTH -> "Length(var=" + varIndex + ",l=" + splitValue + ")";
                case INT -> "Int(var=" + varIndex + ",v=" + splitValue + ")";
            };
        }
    }

    // ------- FDS fields -------

    private final CPIntervalVar[] intervals;
    private final CPIntVar[] intVars;
    private final CPSolver cp;

    /** Decay factor for rating updates (typical values: 0.9 to 0.99). */
    private final double alpha;

    /** Average rating per depth level, used for normalization. */
    private final Map<Integer, double[]> avgRatingByDepth; // [0]=avg, [1]=count

    /** Choices grouped by interval variable index. */
    private final List<Choice>[] intervalChoices;

    /** Choices grouped by integer variable index. */
    private final List<Choice>[] intChoices;

    /** Reusable buffer for domain size snapshots before branching. */
    private final long[] domBeforeBuffer;

    /** State manager for depth tracking. */
    private final StateManager sm;
    private int baseLevel;
    private boolean lastSelectHadUnfixed;

    /**
     * Creates a Failure-Directed Search for the given interval variables.
     *
     * @param intervals the interval variables to decide
     */
    public FDS(CPIntervalVar... intervals) {
        this(0.95, intervals, new CPIntVar[0]);
    }

    /**
     * Creates a Failure-Directed Search for the given 2D matrix of interval variables.
     *
     * @param intervals the 2D array of interval variables to decide
     */
    public FDS(CPIntervalVar[][] intervals) {
        this(0.95, flatten(intervals), new CPIntVar[0]);
    }

    /**
     * Creates a Failure-Directed Search for the given interval variables.
     *
     * @param alpha     decay factor for rating updates (typical: 0.9 to 0.99)
     * @param intervals the interval variables to decide
     */
    public FDS(double alpha, CPIntervalVar... intervals) {
        this(alpha, intervals, new CPIntVar[0]);
    }

    /**
     * Creates a Failure-Directed Search for the given 2D matrix of interval variables.
     *
     * @param alpha     decay factor for rating updates (typical: 0.9 to 0.99)
     * @param intervals the 2D array of interval variables to decide
     */
    public FDS(double alpha, CPIntervalVar[][] intervals) {
        this(alpha, flatten(intervals), new CPIntVar[0]);
    }

    /**
     * Creates a Failure-Directed Search for integer variables.
     */
    public FDS(CPIntVar... intVars) {
        this(0.95, new CPIntervalVar[0], intVars);
    }

    /**
     * Creates a Failure-Directed Search for a 2D matrix of integer variables.
     */
    public FDS(CPIntVar[][] intVars) {
        this(0.95, new CPIntervalVar[0], flatten(intVars));
    }

    /**
     * Creates a Failure-Directed Search for integer variables.
     */
    public FDS(double alpha, CPIntVar... intVars) {
        this(alpha, new CPIntervalVar[0], intVars);
    }

    /**
     * Creates a Failure-Directed Search for a 2D matrix of integer variables.
     */
    public FDS(double alpha, CPIntVar[][] intVars) {
        this(alpha, new CPIntervalVar[0], flatten(intVars));
    }

    /**
     * Creates a Failure-Directed Search for mixed interval and integer decisions.
     */
    public FDS(CPIntervalVar[] intervals, CPIntVar... intVars) {
        this(0.95, intervals, intVars);
    }

    /**
     * Creates a Failure-Directed Search for mixed 2D interval and integer decisions.
     */
    public FDS(CPIntervalVar[][] intervals, CPIntVar... intVars) {
        this(0.95, flatten(intervals), intVars);
    }

    /**
     * Creates a Failure-Directed Search for mixed 2D interval and integer decisions.
     */
    public FDS(double alpha, CPIntervalVar[][] intervals, CPIntVar... intVars) {
        this(alpha, flatten(intervals), intVars);
    }

    private static CPIntervalVar[] flatten(CPIntervalVar[][] intervals) {
        if (intervals == null) return new CPIntervalVar[0];
        return Arrays.stream(intervals)
                .filter(Objects::nonNull)
                .flatMap(Arrays::stream)
                .filter(Objects::nonNull)
                .toArray(CPIntervalVar[]::new);
    }

    private static CPIntVar[] flatten(CPIntVar[][] intVars) {
        if (intVars == null) return new CPIntVar[0];
        return Arrays.stream(intVars)
                .filter(Objects::nonNull)
                .flatMap(Arrays::stream)
                .filter(Objects::nonNull)
                .toArray(CPIntVar[]::new);
    }

    /**
     * Creates a Failure-Directed Search for mixed interval and integer decisions.
     */
    @SuppressWarnings("unchecked")
    public FDS(double alpha, CPIntervalVar[] intervals, CPIntVar... intVars) {
        this.intervals = intervals == null ? new CPIntervalVar[0] : intervals;
        this.intVars = intVars == null ? new CPIntVar[0] : intVars;
        if (this.intervals.length == 0 && this.intVars.length == 0) {
            throw new IllegalArgumentException("FDS requires at least one decision variable");
        }
        this.cp = resolveSolver(this.intervals, this.intVars);
        this.sm = cp.getStateManager();
        this.alpha = alpha;
        this.baseLevel = sm.getLevel();
        this.avgRatingByDepth = new HashMap<>();
        this.intervalChoices = new List[this.intervals.length];
        for (int i = 0; i < this.intervals.length; i++) {
            this.intervalChoices[i] = new ArrayList<>();
        }
        this.intChoices = new List[this.intVars.length];
        for (int i = 0; i < this.intVars.length; i++) {
            this.intChoices[i] = new ArrayList<>();
        }
        this.domBeforeBuffer = new long[this.intervals.length + this.intVars.length];

        generateInitialChoices();
    }

    private static CPSolver resolveSolver(CPIntervalVar[] intervals, CPIntVar[] intVars) {
        CPSolver solver = null;
        for (CPIntervalVar var : intervals) {
            if (solver == null) solver = var.getSolver();
            else if (solver != var.getSolver()) {
                throw new IllegalArgumentException("All variables must belong to the same solver");
            }
        }
        for (CPIntVar var : intVars) {
            if (solver == null) solver = var.getSolver();
            else if (solver != var.getSolver()) {
                throw new IllegalArgumentException("All variables must belong to the same solver");
            }
        }
        return solver;
    }

    /**
     * Resets the depth base level to the current solver level while keeping learned choice ratings.
     */
    public void resetLevel() {
        this.baseLevel = sm.getLevel();
    }

    /**
     * Generate the initial set of choices.
     * <p>
     * Following Section 6.1 of the paper, the initial set only needs to ensure
     * that if all choices are decided, every interval variable either is absent
     * or has a mandatory part (i.e., lct - est &lt; 2 * duration).
     * <p>
     * For each interval variable, we generate:
     * <ul>
     *   <li>A presence choice if it is optional.</li>
     *   <li>Start time choices by binary splitting the current start domain.</li>
     *   <li>Length choices by binary splitting the current length domain (if not fixed).</li>
     * </ul>
     * We limit the number of choices to O(log(domainSize)) per variable.
     */
    private void generateInitialChoices() {
        for (int i = 0; i < intervals.length; i++) {
            CPIntervalVar var = intervals[i];

            // Presence choice for optional variables
            if (var.isOptional()) {
                intervalChoices[i].add(new Choice(ChoiceType.PRESENCE, i, 0));
            }

            // Start time choices via binary splitting
            addBinarySplitChoices(intervalChoices[i], ChoiceType.START, i, var.startMin(), var.startMax());

            // Length choices via binary splitting (if not fixed)
            if (var.lengthMin() < var.lengthMax()) {
                addBinarySplitChoices(intervalChoices[i], ChoiceType.LENGTH, i, var.lengthMin(), var.lengthMax());
            }
        }

        for (int i = 0; i < intVars.length; i++) {
            int min = intVars[i].min();
            int max = intVars[i].max();
            if (min < max) {
                addBinarySplitChoices(intChoices[i], ChoiceType.INT, i, min, max);
            }
        }
    }

    /**
     * Maximum number of initial choices per variable per type.
     * Limits to O(log(domainSize)) levels of the binary splitting tree.
     * Additional choices are generated lazily via {@link #generateAdditionalChoices()}.
     */
    private static final int MAX_INITIAL_CHOICES_PER_VAR = 30;

    /**
     * Add binary split choices for the range [lo, hi] using BFS-order traversal.
     * Only the first few levels of the binary splitting tree are generated
     * (up to {@link #MAX_INITIAL_CHOICES_PER_VAR} choices), ensuring O(log(domainSize))
     * choices per variable. More choices are generated on demand when needed.
     */
    private void addBinarySplitChoices(List<Choice> target, ChoiceType type, int varIndex, int lo, int hi) {
        if (lo >= hi) return;
        int count = 0;
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{lo, hi});
        while (!queue.isEmpty() && count < MAX_INITIAL_CHOICES_PER_VAR) {
            int[] range = queue.poll();
            int l = range[0], h = range[1];
            if (l >= h) continue;
            int mid = l + ((h - l) >>> 1);
            target.add(new Choice(type, varIndex, mid));
            count++;
            queue.add(new int[]{l, mid});
            queue.add(new int[]{mid + 1, h});
        }
    }

    /**
     * Get current depth relative to the base level when FDS was created.
     */
    private int currentDepth() {
        return Math.max(0, sm.getLevel() - baseLevel);
    }

    /**
     * Get the average rating structure for a given depth.
     * Returns a double[2] where [0] = running average, [1] = count.
     */
    private double[] getAvgRating(int depth) {
        return avgRatingByDepth.computeIfAbsent(depth, k -> new double[]{1.0, 0.0});
    }

    /**
     * Compute the search space size estimate R for the given variables.
     * R = product(domainSize_after / domainSize_before) over all variables.
     * Returns a value between 0 and 1.
     */
    private double computeSearchSpaceReduction(long[] domainSizesBefore) {
        double ratio = 1.0;
        int p = 0;
        for (int i = 0; i < intervals.length; i++) {
            long before = domainSizesBefore[p++];
            if (before > 1L) {
                long after = domainSize(intervals[i]);
                if (after != before) {
                    ratio *= (double) after / (double) before;
                }
            }
        }
        for (int i = 0; i < intVars.length; i++) {
            long before = domainSizesBefore[p++];
            if (before > 1L) {
                long after = Math.max(1L, intVars[i].size());
                if (after != before) {
                    ratio *= (double) after / (double) before;
                }
            }
        }
        return Math.min(ratio, 1.0);
    }

    /**
     * Estimate domain size for an interval variable.
     */
    private long domainSize(CPIntervalVar var) {
        if (var.isAbsent()) return 1L;
        long startRange = Math.max(1L, (long) var.startMax() - var.startMin() + 1L);
        long lengthRange = Math.max(1L, (long) var.lengthMax() - var.lengthMin() + 1L);
        if (var.isOptional()) startRange++; // +1 for the absent possibility
        return startRange * lengthRange;
    }

    /**
     * Snapshot domain sizes for all decision variables (before a branch).
     */
    private long[] snapshotDomainSizes() {
        int p = 0;
        for (int i = 0; i < intervals.length; i++) {
            domBeforeBuffer[p++] = domainSize(intervals[i]);
        }
        for (int i = 0; i < intVars.length; i++) {
            domBeforeBuffer[p++] = Math.max(1L, intVars[i].size());
        }
        return domBeforeBuffer;
    }

    /**
     * Update the rating of a branch after exploring it.
     * Implements formulas (2) and (3) from the paper:
     * <pre>
     *   localRating = 0              if the branch fails immediately
     *                 1 + R          otherwise
     *   rating_+/- = alpha * rating_+/- + (1 - alpha) * localRating / avgRating[d]
     * </pre>
     *
     * @param choice     the choice whose branch was explored
     * @param isPositive true if the positive branch was explored
     * @param failed     true if the branch failed immediately
     * @param R          the search space reduction ratio (used if branch did not fail)
     */
    private void updateRating(Choice choice, boolean isPositive, boolean failed, double R) {
        double localRating = failed ? 0.0 : 1.0 + R;

        int d = currentDepth();
        double[] avg = getAvgRating(d);
        double avgVal = avg[0] > 0 ? avg[0] : 1.0;
        double normalizedRating = localRating / avgVal;

        // Update the branch rating with exponential decay
        if (isPositive) {
            choice.ratingPos = alpha * choice.ratingPos + (1.0 - alpha) * normalizedRating;
        } else {
            choice.ratingNeg = alpha * choice.ratingNeg + (1.0 - alpha) * normalizedRating;
        }

        // Update average rating for this depth (incremental mean)
        avg[1] += 1.0;
        avg[0] += (localRating - avg[0]) / avg[1];
    }

    /**
     * The FDS branching strategy, implementing the {@code Supplier<Runnable[]>} interface.
     * <p>
     * This method implements the core FDS algorithm from Algorithm 1 in the paper.
     * At each branching point:
     * <ol>
     *   <li>Select the applicable choice with the best (lowest) rating.</li>
     *   <li>Skip waiting and resolved choices.</li>
     *   <li>Return two branches: the better-rated branch first (heading into conflict).</li>
     * </ol>
     * If all initial choices are resolved but variables remain unfixed,
     * additional choices are generated dynamically.
     *
     * @return array of branching closures, or EMPTY if all tasks are fixed
     */
    @Override
    public Runnable[] get() {
        Choice bestChoice = selectBestChoice();
        if (bestChoice == null) {
            if (!lastSelectHadUnfixed) {
                return Searches.EMPTY;
            }
            if (generateAdditionalChoices()) {
                bestChoice = selectBestChoice();
            }
            if (bestChoice == null) {
                return Searches.EMPTY;
            }
        }

        final Choice chosen = bestChoice;
        // Determine which branch to explore first (better rating = lower value → heads into conflict)
        boolean positiveFirst = chosen.ratingPos <= chosen.ratingNeg;

        if (positiveFirst) {
            return Searches.branch(
                    makeBranch(chosen, true),
                    makeBranch(chosen, false)
            );
        } else {
            return Searches.branch(
                    makeBranch(chosen, false),
                    makeBranch(chosen, true)
            );
        }
    }

    private Choice selectBestChoice() {
        Choice bestChoice = null;
        double bestRating = Double.MAX_VALUE;
        int bestDomSize = Integer.MAX_VALUE;
        int bestMidDist = Integer.MAX_VALUE;
        boolean anyUnfixed = false;

        for (int i = 0; i < intervals.length; i++) {
            CPIntervalVar var = intervals[i];
            if (var.isFixed()) continue;
            anyUnfixed = true;
            if (var.isAbsent()) continue;
            boolean optional = var.isOptional();
            int sMin = var.startMin();
            int sMax = var.startMax();
            int lMin = var.lengthMin();
            int lMax = var.lengthMax();
            int sMid = sMin + ((sMax - sMin) >>> 1);
            int lMid = lMin + ((lMax - lMin) >>> 1);

            List<Choice> choices = intervalChoices[i];
            for (int k = 0, n = choices.size(); k < n; k++) {
                Choice c = choices.get(k);
                int domSize;
                int midDist;
                switch (c.type) {
                    case PRESENCE -> {
                        if (!optional) continue;
                        domSize = 2;
                        midDist = 0;
                    }
                    case START -> {
                        if (optional || sMin > c.splitValue || sMax <= c.splitValue) continue;
                        domSize = sMax - sMin + 1;
                        midDist = Math.abs(c.splitValue - sMid);
                    }
                    case LENGTH -> {
                        if (optional || lMin > c.splitValue || lMax <= c.splitValue) continue;
                        domSize = lMax - lMin + 1;
                        midDist = Math.abs(c.splitValue - lMid);
                    }
                    default -> {
                        continue;
                    }
                }
                double r = c.rating();
                if (r < bestRating - 1e-12
                        || (Math.abs(r - bestRating) <= 1e-12
                        && (domSize < bestDomSize || (domSize == bestDomSize && midDist < bestMidDist)))) {
                    bestRating = r;
                    bestDomSize = domSize;
                    bestMidDist = midDist;
                    bestChoice = c;
                }
            }
        }

        for (int i = 0; i < intVars.length; i++) {
            CPIntVar v = intVars[i];
            int min = v.min();
            int max = v.max();
            if (min == max) {
                continue;
            }
            anyUnfixed = true;
            int domSize = v.size();
            int mid = min + ((max - min) >>> 1);
            List<Choice> choices = intChoices[i];
            for (int k = 0, n = choices.size(); k < n; k++) {
                Choice c = choices.get(k);
                if (min <= c.splitValue && max > c.splitValue) {
                    double r = c.rating();
                    int midDist = Math.abs(c.splitValue - mid);
                    if (r < bestRating - 1e-12
                            || (Math.abs(r - bestRating) <= 1e-12
                            && (domSize < bestDomSize || (domSize == bestDomSize && midDist < bestMidDist)))) {
                        bestRating = r;
                        bestDomSize = domSize;
                        bestMidDist = midDist;
                        bestChoice = c;
                    }
                }
            }
        }

        lastSelectHadUnfixed = anyUnfixed;
        return anyUnfixed ? bestChoice : null;
    }

    /**
     * Create a branching closure for one side of a choice.
     * The closure applies the branch, updates the rating, and re-throws any failure.
     *
     * @param choice     the choice to branch on
     * @param isPositive true for positive branch, false for negative
     * @return a Runnable that applies the branch
     */
    private Runnable makeBranch(Choice choice, boolean isPositive) {
        return () -> {
            long[] domBefore = snapshotDomainSizes();
            try {
                if (choice.type == ChoiceType.INT) {
                    CPIntVar v = intVars[choice.varIndex];
                    if (isPositive) {
                        v.removeAbove(choice.splitValue);
                    } else {
                        v.removeBelow(choice.splitValue + 1);
                    }
                    cp.fixPoint();
                } else if (isPositive) {
                    choice.applyPositive(intervals[choice.varIndex], cp);
                } else {
                    choice.applyNegative(intervals[choice.varIndex], cp);
                }
            } catch (InconsistencyException e) {
                updateRating(choice, isPositive, true, 0);
                throw e;
            }
            double R = computeSearchSpaceReduction(domBefore);
            updateRating(choice, isPositive, false, R);
        };
    }

    /**
     * Generate additional choices for variables that are not yet fixed.
     * This is called when all initial choices are resolved but some variables remain unfixed
     * (as described in Section 6.1 of the paper).
     */
    private boolean generateAdditionalChoices() {
        boolean added = false;
        for (int i = 0; i < intervals.length; i++) {
            CPIntervalVar var = intervals[i];
            if (var.isFixed() || var.isAbsent()) continue;

            // Presence choice for still-optional variables
            if (var.isOptional()) {
                intervalChoices[i].add(new Choice(ChoiceType.PRESENCE, i, 0));
                added = true;
                continue; // presence must be decided first
            }

            // Start time choices for the remaining start domain
            if (var.startMin() < var.startMax()) {
                int mid = var.startMin() + ((var.startMax() - var.startMin()) >>> 1);
                intervalChoices[i].add(new Choice(ChoiceType.START, i, mid));
                added = true;
            }

            // Length choices for the remaining length domain
            if (var.lengthMin() < var.lengthMax()) {
                int mid = var.lengthMin() + ((var.lengthMax() - var.lengthMin()) >>> 1);
                intervalChoices[i].add(new Choice(ChoiceType.LENGTH, i, mid));
                added = true;
            }
        }

        for (int i = 0; i < intVars.length; i++) {
            int min = intVars[i].min();
            int max = intVars[i].max();
            if (min < max) {
                int mid = min + ((max - min) >>> 1);
                intChoices[i].add(new Choice(ChoiceType.INT, i, mid));
                added = true;
            }
        }
        return added;
    }
}

