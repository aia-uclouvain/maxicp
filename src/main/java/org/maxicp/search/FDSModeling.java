/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 */

package org.maxicp.search;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.ModelProxy;
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
        PRESENCE, START, LENGTH
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
            }
        }

        @Override
        public String toString() {
            return switch (type) {
                case PRESENCE -> "Presence(var=" + varIndex + ")";
                case START -> "Start(var=" + varIndex + ",t=" + splitValue + ")";
                case LENGTH -> "Length(var=" + varIndex + ",l=" + splitValue + ")";
            };
        }
    }

    // ------- FDS fields -------

    private final IntervalVar[] intervals;
    private final ModelProxy model;

    /** Decay factor for rating updates (typical values: 0.9 to 0.99). */
    private final double alpha;

    /** Average rating per depth level, used for normalization. */
    private final Map<Integer, double[]> avgRatingByDepth; // [0]=avg, [1]=count

    /** All choices generated for this search. */
    private final List<Choice> allChoices;

    /** State manager for depth tracking. */
    private final StateManager sm;
    private final int baseLevel;

    /**
     * Creates a Failure-Directed Search for the given interval variables.
     *
     * @param intervals the interval variables to decide
     */
    public FDSModeling(IntervalVar... intervals) {
        this(0.95, intervals);
    }

    /**
     * Creates a Failure-Directed Search for the given interval variables.
     *
     * @param alpha     decay factor for rating updates (typical: 0.9 to 0.99)
     * @param intervals the interval variables to decide
     */
    public FDSModeling(double alpha, IntervalVar... intervals) {
        this.intervals = intervals;
        this.model = intervals[0].getModelProxy();
        this.sm = model.getConcreteModel().getStateManager();
        this.alpha = alpha;
        this.baseLevel = sm.getLevel();
        this.avgRatingByDepth = new HashMap<>();
        this.allChoices = new ArrayList<>();

        generateInitialChoices();
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
                allChoices.add(new Choice(ChoiceType.PRESENCE, i, 0));
            }

            // Start time choices via binary splitting
            addBinarySplitChoices(ChoiceType.START, i, var.startMin(), var.startMax());

            // Length choices via binary splitting (if not fixed)
            if (var.lengthMin() < var.lengthMax()) {
                addBinarySplitChoices(ChoiceType.LENGTH, i, var.lengthMin(), var.lengthMax());
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
    private void addBinarySplitChoices(ChoiceType type, int varIndex, int lo, int hi) {
        if (lo >= hi) return;
        int count = 0;
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{lo, hi});
        while (!queue.isEmpty() && count < MAX_INITIAL_CHOICES_PER_VAR) {
            int[] range = queue.poll();
            int l = range[0], h = range[1];
            if (l >= h) continue;
            int mid = l + (h - l) / 2;
            allChoices.add(new Choice(type, varIndex, mid));
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
     * Compute the search space size estimate R for the given intervals.
     * R = product(domainSize_after / domainSize_before) over all variables.
     * Returns a value between 0 and 1.
     */
    private double computeSearchSpaceReduction(long[] domainSizesBefore) {
        double ratio = 1.0;
        for (int i = 0; i < intervals.length; i++) {
            long before = domainSizesBefore[i];
            if (before > 0) {
                long after = domainSize(intervals[i]);
                ratio *= (double) after / (double) before;
            }
        }
        return Math.min(ratio, 1.0);
    }

    /**
     * Estimate domain size for an interval variable.
     */
    private long domainSize(IntervalVar var) {
        if (var.isAbsent()) return 1;
        long startRange = Math.max(1, (long) var.startMax() - var.startMin() + 1);
        long lengthRange = Math.max(1, (long) var.lengthMax() - var.lengthMin() + 1);
        if (var.isOptional()) startRange++; // +1 for the absent possibility
        return startRange * lengthRange;
    }

    /**
     * Snapshot domain sizes for all interval variables (before a branch).
     */
    private long[] snapshotDomainSizes() {
        long[] sizes = new long[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            sizes[i] = domainSize(intervals[i]);
        }
        return sizes;
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
     * Check if all interval variables are fixed.
     */
    private boolean allFixed() {
        for (IntervalVar var : intervals) {
            if (!var.isFixed()) return false;
        }
        return true;
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
        if (allFixed()) {
            return EMPTY;
        }

        // Pick: find the best applicable choice (lowest rating)
        Choice bestChoice = null;
        double bestRating = Double.MAX_VALUE;

        for (Choice c : allChoices) {
            IntervalVar var = intervals[c.varIndex];
            if (c.isWaiting(var) || c.isResolved(var)) {
                continue;
            }
            double r = c.rating();
            if (r < bestRating) {
                bestRating = r;
                bestChoice = c;
            }
        }

        if (bestChoice == null) {
            // No applicable choice found — generate more if needed
            if (!allFixed()) {
                int before = allChoices.size();
                generateAdditionalChoices();
                if (allChoices.size() > before) {
                    return get(); // retry with newly generated choices
                }
            }
            return EMPTY;
        }

        final Choice chosen = bestChoice;
        final IntervalVar var = intervals[chosen.varIndex];

        // Determine which branch to explore first (better rating = lower value → heads into conflict)
        boolean positiveFirst = chosen.ratingPos <= chosen.ratingNeg;

        if (positiveFirst) {
            return branch(
                    makeBranch(chosen, var, true),
                    makeBranch(chosen, var, false)
            );
        } else {
            return branch(
                    makeBranch(chosen, var, false),
                    makeBranch(chosen, var, true)
            );
        }
    }

    /**
     * Create a branching closure for one side of a choice.
     * The closure applies the branch, updates the rating, and re-throws any failure.
     *
     * @param choice     the choice to branch on
     * @param var        the interval variable for this choice
     * @param isPositive true for positive branch, false for negative
     * @return a Runnable that applies the branch
     */
    private Runnable makeBranch(Choice choice, IntervalVar var, boolean isPositive) {
        return () -> {
            long[] domBefore = snapshotDomainSizes();
            try {
                if (isPositive) {
                    choice.applyPositive(var, model);
                } else {
                    choice.applyNegative(var, model);
                }
            } catch (InconsistencyException e) {
                updateRating(choice, isPositive, true, 0);
                throw e;
            }
            long[] domAfter = snapshotDomainSizes();
            double R = computeSearchSpaceReduction(domBefore);
            updateRating(choice, isPositive, false, R);
        };
    }

    /**
     * Generate additional choices for variables that are not yet fixed.
     * This is called when all initial choices are resolved but some variables remain unfixed
     * (as described in Section 6.1 of the paper).
     */
    private void generateAdditionalChoices() {
        for (int i = 0; i < intervals.length; i++) {
            IntervalVar var = intervals[i];
            if (var.isFixed() || var.isAbsent()) continue;

            // Presence choice for still-optional variables
            if (var.isOptional()) {
                allChoices.add(new Choice(ChoiceType.PRESENCE, i, 0));
                continue; // presence must be decided first
            }

            // Start time choices for the remaining start domain
            if (var.startMin() < var.startMax()) {
                int mid = var.startMin() + (var.startMax() - var.startMin()) / 2;
                allChoices.add(new Choice(ChoiceType.START, i, mid));
            }

            // Length choices for the remaining length domain
            if (var.lengthMin() < var.lengthMax()) {
                int mid = var.lengthMin() + (var.lengthMax() - var.lengthMin()) / 2;
                allChoices.add(new Choice(ChoiceType.LENGTH, i, mid));
            }
        }
    }
}

