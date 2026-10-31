                                                                                                                                            /*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 */

package org.maxicp.search;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.concrete.ConcreteModel;
import org.maxicp.state.StateManager;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;
import java.util.function.Supplier;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * Failure-Directed Search (FDS) for scheduling problems with modeling-layer interval variables.
 * <p>
 * This is the modeling-layer adaptation of {@link FDS}, working with {@link IntervalVar}
 * and {@link ModelProxy} instead of {@code CPIntervalVar} and {@code CPSolver}.
 * <p>
 * This search algorithm is designed for a broad class of scheduling problems.
 * Instead of guiding the search towards possible solutions, FDS drives the search
 * into conflicts in order to prove that the current branch is infeasible.
 * Choices that fail the most are preferred (first-fail principle applied also to branch ordering).
 * <p>
 * The algorithm works with binary choices:
 * <ul>
 *   <li><b>Presence choice:</b> whether an optional interval variable is present or absent.</li>
 *   <li><b>Start time choice:</b> whether startOf(v) &le; t or startOf(v) &gt; t (domain splitting).</li>
 *   <li><b>Length choice:</b> whether length(v) &le; l or length(v) &gt; l (domain splitting).</li>
 * </ul>
 * <p>
 * Reference: Vilím, P., Laborie, P., Shaw, P. (2015).
 * Failure-directed Search for Constraint-based Scheduling.
 * CPAIOR 2015.
 *
 * @author pschaus
 */
public class FDSModeling implements Supplier<Runnable[]> {

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
     * </ul>
     */
    private static final class Choice {
        final ChoiceType type;
        final int varIndex;
        final int splitValue; // only used for START and LENGTH choices

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
        boolean isResolved(IntervalVar var) {
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
        boolean isWaiting(IntervalVar var) {
            return switch (type) {
                case PRESENCE -> false;
                case START, LENGTH -> var.isOptional();
                case INT -> false;
            };
        }

        /**
         * Apply the positive branch.
         */
        void applyPositive(IntervalVar var, ModelProxy model) {
            switch (type) {
                case PRESENCE -> model.add(present(var));
                case START -> model.add(startBefore(var, splitValue));   // start <= splitValue
                case LENGTH -> model.add(le(length(var), splitValue));   // length <= splitValue
                case INT -> throw new IllegalStateException("INT choice must be applied with IntExpression");
            }
        }

        /**
         * Apply the negative branch.
         */
        void applyNegative(IntervalVar var, ModelProxy model) {
            switch (type) {
                case PRESENCE -> model.add(not(present(var)));
                case START -> model.add(startAfter(var, splitValue + 1));    // start > splitValue
                case LENGTH -> model.add(lt(splitValue, length(var)));   // splitValue < length, i.e. length > splitValue
                case INT -> throw new IllegalStateException("INT choice must be applied with IntExpression");
            }
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

    private final IntervalVar[] intervals;
    private final IntExpression[] intExpressions;
    private final ModelProxy model;

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

    /** Cached concrete model and direct CP variable references to avoid ThreadLocal/StateMap overhead per node. */
    private ConcreteModel cachedConcreteModel;
    private CPSolver cpSolver;
    private CPIntervalVar[] cpIntervals;
    private CPIntVar[] cpIntVars;

    /** State manager for depth tracking. */
    private StateManager sm;
    private int baseLevel;
    private boolean initializedChoices;
    private boolean lastSelectHadUnfixed;

    /**
     * Creates a Failure-Directed Search for the given interval variables.
     *
     * @param intervals the interval variables to decide
     */
    public FDSModeling(IntervalVar... intervals) {
        this(0.95, intervals, new IntExpression[0]);
    }

    /**
     * Creates a Failure-Directed Search for the given 2D matrix of interval variables.
     *
     * @param intervals the 2D array of interval variables to decide
     */
    public FDSModeling(IntervalVar[][] intervals) {
        this(0.95, flatten(intervals), new IntExpression[0]);
    }

    /**
     * Creates a Failure-Directed Search for the given interval variables.
     *
     * @param alpha     decay factor for rating updates (typical: 0.9 to 0.99)
     * @param intervals the interval variables to decide
     */
    public FDSModeling(double alpha, IntervalVar... intervals) {
        this(alpha, intervals, new IntExpression[0]);
    }

    /**
     * Creates a Failure-Directed Search for the given 2D matrix of interval variables.
     *
     * @param alpha     decay factor for rating updates (typical: 0.9 to 0.99)
     * @param intervals the 2D array of interval variables to decide
     */
    public FDSModeling(double alpha, IntervalVar[][] intervals) {
        this(alpha, flatten(intervals), new IntExpression[0]);
    }

    /**
     * Creates a Failure-Directed Search for integer expressions.
     */
    public FDSModeling(IntExpression... intExpressions) {
        this(0.95, new IntervalVar[0], intExpressions);
    }

    /**
     * Creates a Failure-Directed Search for a 2D matrix of integer expressions.
     */
    public FDSModeling(IntExpression[][] intExpressions) {
        this(0.95, new IntervalVar[0], flatten(intExpressions));
    }

    /**
     * Creates a Failure-Directed Search for integer expressions.
     */
    public FDSModeling(double alpha, IntExpression... intExpressions) {
        this(alpha, new IntervalVar[0], intExpressions);
    }

    /**
     * Creates a Failure-Directed Search for a 2D matrix of integer expressions.
     */
    public FDSModeling(double alpha, IntExpression[][] intExpressions) {
        this(alpha, new IntervalVar[0], flatten(intExpressions));
    }

    /**
     * Creates a Failure-Directed Search for mixed interval and integer decisions.
     */
    public FDSModeling(IntervalVar[] intervals, IntExpression... intExpressions) {
        this(0.95, intervals, intExpressions);
    }

    /**
     * Creates a Failure-Directed Search for mixed 2D interval and integer decisions.
     */
    public FDSModeling(IntervalVar[][] intervals, IntExpression... intExpressions) {
        this(0.95, flatten(intervals), intExpressions);
    }

    /**
     * Creates a Failure-Directed Search for mixed 2D interval and integer decisions.
     */
    public FDSModeling(double alpha, IntervalVar[][] intervals, IntExpression... intExpressions) {
        this(alpha, flatten(intervals), intExpressions);
    }

    private static IntervalVar[] flatten(IntervalVar[][] intervals) {
        if (intervals == null) return new IntervalVar[0];
        return Arrays.stream(intervals)
                .filter(Objects::nonNull)
                .flatMap(Arrays::stream)
                .filter(Objects::nonNull)
                .toArray(IntervalVar[]::new);
    }

    private static IntExpression[] flatten(IntExpression[][] intExpressions) {
        if (intExpressions == null) return new IntExpression[0];
        return Arrays.stream(intExpressions)
                .filter(Objects::nonNull)
                .flatMap(Arrays::stream)
                .filter(Objects::nonNull)
                .toArray(IntExpression[]::new);
    }

    /**
     * Creates a Failure-Directed Search for mixed interval and integer decisions.
     */
    @SuppressWarnings("unchecked")
    public FDSModeling(double alpha, IntervalVar[] intervals, IntExpression... intExpressions) {
        this.intervals = intervals == null ? new IntervalVar[0] : intervals;
        this.intExpressions = intExpressions == null ? new IntExpression[0] : intExpressions;
        if (this.intervals.length == 0 && this.intExpressions.length == 0) {
            throw new IllegalArgumentException("FDSModeling requires at least one decision variable");
        }
        this.model = resolveModelProxy(this.intervals, this.intExpressions);
        this.alpha = alpha;
        this.avgRatingByDepth = new HashMap<>();
        this.intervalChoices = new List[this.intervals.length];
        for (int i = 0; i < this.intervals.length; i++) {
            this.intervalChoices[i] = new ArrayList<>();
        }
        this.intChoices = new List[this.intExpressions.length];
        for (int i = 0; i < this.intExpressions.length; i++) {
            this.intChoices[i] = new ArrayList<>();
        }
        this.domBeforeBuffer = new long[this.intervals.length + this.intExpressions.length];

        if (this.model == null || this.model.isConcrete()) {
            ensureConcreteCache();
            generateInitialChoices();
            this.initializedChoices = true;
        }
    }

    /**
     * Resets the depth base level to the current solver level while keeping learned choice ratings.
     */
    public void resetLevel() {
        ensureConcreteCache();
        if (sm != null) {
            this.baseLevel = sm.getLevel();
        }
    }

    private void ensureConcreteCache() {
        if (model == null) {
            if (cpSolver == null) {
                cpIntervals = new CPIntervalVar[intervals.length];
                for (int i = 0; i < intervals.length; i++) {
                    cpIntervals[i] = (CPIntervalVar) intervals[i];
                    if (cpSolver == null) cpSolver = cpIntervals[i].getSolver();
                }
                cpIntVars = new CPIntVar[intExpressions.length];
                for (int i = 0; i < intExpressions.length; i++) {
                    cpIntVars[i] = (CPIntVar) intExpressions[i];
                    if (cpSolver == null) cpSolver = cpIntVars[i].getSolver();
                }
                if (cpSolver != null) {
                    sm = cpSolver.getStateManager();
                    baseLevel = sm.getLevel();
                }
            }
            if (!initializedChoices) {
                generateInitialChoices();
                initializedChoices = true;
            }
            return;
        }
        ConcreteModel cm = model.getConcreteModel();
        if (cm != cachedConcreteModel) {
            cachedConcreteModel = cm;
            sm = cm.getStateManager();
            baseLevel = sm.getLevel();
            if (cm instanceof ConcreteCPModel cpm) {
                cpSolver = cpm.solver;
                cpIntervals = new CPIntervalVar[intervals.length];
                for (int i = 0; i < intervals.length; i++) {
                    cpIntervals[i] = cpm.getCPVar(intervals[i]);
                }
                cpIntVars = new CPIntVar[intExpressions.length];
                for (int i = 0; i < intExpressions.length; i++) {
                    cpIntVars[i] = cpm.getCPVar(intExpressions[i]);
                }
            } else {
                cpSolver = null;
                cpIntervals = null;
                cpIntVars = null;
            }
        }
        if (!initializedChoices) {
            generateInitialChoices();
            initializedChoices = true;
        }
    }

    private static ModelProxy resolveModelProxy(IntervalVar[] intervals, IntExpression[] intExpressions) {
        ModelProxy proxy = null;
        for (IntervalVar var : intervals) {
            if (proxy == null) proxy = var.getModelProxy();
            else if (proxy != var.getModelProxy()) {
                throw new IllegalArgumentException("All variables must belong to the same model");
            }
        }
        for (IntExpression expr : intExpressions) {
            if (proxy == null) proxy = expr.getModelProxy();
            else if (proxy != expr.getModelProxy()) {
                throw new IllegalArgumentException("All variables must belong to the same model");
            }
        }
        return proxy;
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
            IntervalVar var = intervals[i];

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

        for (int i = 0; i < intExpressions.length; i++) {
            int min = cpIntVars != null ? cpIntVars[i].min() : intExpressions[i].min();
            int max = cpIntVars != null ? cpIntVars[i].max() : intExpressions[i].max();
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
     * Estimate domain size for an interval variable.
     */
    private long intervalDomainSize(int i) {
        if (cpIntervals != null) {
            CPIntervalVar var = cpIntervals[i];
            if (var.isAbsent()) return 1L;
            long startRange = Math.max(1L, (long) var.startMax() - var.startMin() + 1L);
            long lengthRange = Math.max(1L, (long) var.lengthMax() - var.lengthMin() + 1L);
            if (var.isOptional()) startRange++;
            return startRange * lengthRange;
        }
        IntervalVar var = intervals[i];
        if (var.isAbsent()) return 1L;
        long startRange = Math.max(1L, (long) var.startMax() - var.startMin() + 1L);
        long lengthRange = Math.max(1L, (long) var.lengthMax() - var.lengthMin() + 1L);
        if (var.isOptional()) startRange++; // +1 for the absent possibility
        return startRange * lengthRange;
    }

    /**
     * Estimate domain size for an integer expression.
     */
    private long intDomainSize(int i) {
        int sz = cpIntVars != null ? cpIntVars[i].size() : intExpressions[i].size();
        return Math.max(1L, sz);
    }

    /**
     * Snapshot domain sizes for all decision variables (before a branch).
     */
    private long[] snapshotDomainSizes() {
        int p = 0;
        for (int i = 0; i < intervals.length; i++) {
            domBeforeBuffer[p++] = intervalDomainSize(i);
        }
        for (int i = 0; i < intExpressions.length; i++) {
            domBeforeBuffer[p++] = intDomainSize(i);
        }
        return domBeforeBuffer;
    }

    private double computeSearchSpaceReduction(long[] domainSizesBefore) {
        double ratio = 1.0;
        int p = 0;
        for (int i = 0; i < intervals.length; i++) {
            long before = domainSizesBefore[p++];
            if (before > 1L) {
                long after = intervalDomainSize(i);
                if (after != before) {
                    ratio *= (double) after / (double) before;
                }
            }
        }
        for (int i = 0; i < intExpressions.length; i++) {
            long before = domainSizesBefore[p++];
            if (before > 1L) {
                long after = intDomainSize(i);
                if (after != before) {
                    ratio *= (double) after / (double) before;
                }
            }
        }
        return Math.min(ratio, 1.0);
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

    @Override
    public Runnable[] get() {
        ensureConcreteCache();

        Choice bestChoice = selectBestChoice();
        if (bestChoice == null) {
            if (!lastSelectHadUnfixed) {
                return EMPTY;
            }
            if (generateAdditionalChoices()) {
                bestChoice = selectBestChoice();
            }
            if (bestChoice == null) {
                return EMPTY;
            }
        }

        final Choice chosen = bestChoice;
        // Determine which branch to explore first (better rating = lower value → heads into conflict)
        boolean positiveFirst = chosen.ratingPos <= chosen.ratingNeg;

        if (positiveFirst) {
            return branch(
                    makeBranch(chosen, true),
                    makeBranch(chosen, false)
            );
        } else {
            return branch(
                    makeBranch(chosen, false),
                    makeBranch(chosen, true)
            );
        }
    }

    private Choice selectBestChoice() {
        Choice bestChoice = null;
        double bestRating = Double.MAX_VALUE;
        boolean anyUnfixed = false;

        for (int i = 0; i < intervals.length; i++) {
            boolean fixed;
            boolean absent;
            boolean optional;
            int sMin, sMax, lMin, lMax;
            if (cpIntervals != null) {
                CPIntervalVar var = cpIntervals[i];
                fixed = var.isFixed();
                if (fixed) continue;
                anyUnfixed = true;
                absent = var.isAbsent();
                if (absent) continue;
                optional = var.isOptional();
                sMin = var.startMin();
                sMax = var.startMax();
                lMin = var.lengthMin();
                lMax = var.lengthMax();
            } else {
                IntervalVar var = intervals[i];
                fixed = var.isFixed();
                if (fixed) continue;
                anyUnfixed = true;
                absent = var.isAbsent();
                if (absent) continue;
                optional = var.isOptional();
                sMin = var.startMin();
                sMax = var.startMax();
                lMin = var.lengthMin();
                lMax = var.lengthMax();
            }

            List<Choice> choices = intervalChoices[i];
            for (int k = 0, n = choices.size(); k < n; k++) {
                Choice c = choices.get(k);
                boolean applicable = switch (c.type) {
                    case PRESENCE -> optional;
                    case START -> !optional && sMin <= c.splitValue && sMax > c.splitValue;
                    case LENGTH -> !optional && lMin <= c.splitValue && lMax > c.splitValue;
                    case INT -> false;
                };
                if (applicable) {
                    double r = c.rating();
                    if (r < bestRating) {
                        bestRating = r;
                        bestChoice = c;
                    }
                }
            }
        }

        for (int i = 0; i < intExpressions.length; i++) {
            int min, max, size;
            if (cpIntVars != null) {
                CPIntVar v = cpIntVars[i];
                min = v.min();
                max = v.max();
                if (min == max) continue;
                size = v.size();
            } else {
                IntExpression x = intExpressions[i];
                min = x.min();
                max = x.max();
                if (min == max) continue;
                size = x.size();
            }
            anyUnfixed = true;
            double mid = (min + max - 1) * 0.5;
            List<Choice> choices = intChoices[i];
            for (int k = 0, n = choices.size(); k < n; k++) {
                Choice c = choices.get(k);
                if (min <= c.splitValue && max > c.splitValue) {
                    double r = c.rating() + size * 1e-9 + Math.abs(c.splitValue - mid) * 1e-12;
                    if (r < bestRating) {
                        bestRating = r;
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
                    if (cpIntVars != null) {
                        CPIntVar v = cpIntVars[choice.varIndex];
                        if (isPositive) {
                            v.removeAbove(choice.splitValue);
                        } else {
                            v.removeBelow(choice.splitValue + 1);
                        }
                        cpSolver.fixPoint();
                    } else {
                        IntExpression x = intExpressions[choice.varIndex];
                        if (isPositive) {
                            model.add(le(x, choice.splitValue));
                        } else {
                            model.add(lt(choice.splitValue, x));
                        }
                    }
                } else if (cpIntervals != null) {
                    CPIntervalVar var = cpIntervals[choice.varIndex];
                    if (isPositive) {
                        switch (choice.type) {
                            case PRESENCE -> var.setPresent();
                            case START -> var.setStartMax(choice.splitValue);
                            case LENGTH -> var.setLengthMax(choice.splitValue);
                            case INT -> throw new IllegalStateException();
                        }
                    } else {
                        switch (choice.type) {
                            case PRESENCE -> var.setAbsent();
                            case START -> var.setStartMin(choice.splitValue + 1);
                            case LENGTH -> var.setLengthMin(choice.splitValue + 1);
                            case INT -> throw new IllegalStateException();
                        }
                    }
                    cpSolver.fixPoint();
                } else if (isPositive) {
                    choice.applyPositive(intervals[choice.varIndex], model);
                } else {
                    choice.applyNegative(intervals[choice.varIndex], model);
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
            IntervalVar var = intervals[i];
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

        for (int i = 0; i < intExpressions.length; i++) {
            int min = cpIntVars != null ? cpIntVars[i].min() : intExpressions[i].min();
            int max = cpIntVars != null ? cpIntVars[i].max() : intExpressions[i].max();
            if (min < max) {
                int mid = min + ((max - min) >>> 1);
                intChoices[i].add(new Choice(ChoiceType.INT, i, mid));
                added = true;
            }
        }
        return added;
    }
}

