/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */
package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.InversePerm;
import org.maxicp.cp.engine.constraints.setvar.IsIncluded;
import org.maxicp.cp.engine.constraints.setvar.IsSubset;
import org.maxicp.cp.engine.core.*;
import org.maxicp.util.algo.DistanceMatrix;

import java.util.PriorityQueue;

import static org.maxicp.Constants.HORIZON;

/**
 * A global constraint that enforces a permutation (ordering) of interval variables
 * with minimum transition times, using set-variable-based propagation for stronger
 * filtering than the binary decomposition {@link NoOverlapWithPositionDecomposition}.
 *
 * <h2>Model</h2>
 * <p>Each interval {@code i} is assigned a position {@code posOfInterval[i]} in {@code 0..n-1}
 * (all positions distinct via an {@link InversePerm} channeling to {@code intervalInPos}).
 * A global {@link NoOverlap} enforces that intervals do not overlap.
 *
 * <h2>Set-based position reasoning</h2>
 * <p>For each interval {@code i}, a set variable {@code befores[i]} represents the set of
 * intervals that precede {@code i} in the sequence. Its cardinality is channeled to the
 * position: {@code |befores[i]| = posOfInterval[i]}. The following equivalences are posted
 * for every ordered pair {@code (i, j), i ≠ j}:
 * <ul>
 *   <li>{@code i ∈ befores[j]} &hArr; {@code posOfInterval[i] < posOfInterval[j]}
 *       (via {@link org.maxicp.cp.engine.constraints.setvar.IsIncluded IsIncluded} + {@code isLt})</li>
 *   <li>{@code befores[i] ⊆ befores[j]} &hArr; {@code i ∈ befores[j]}
 *       (via {@link IsSubset} — enforces <em>transitivity</em>: if i precedes j,
 *       every interval before i is also before j)</li>
 *   <li>{@code i ∈ befores[j]} &hArr; {@code end(i) + trans[i][j] ≤ start(j)}
 *       (via {@link IsEndBeforeStartWithTransition} — channels temporal ordering)</li>
 *   <li>{@code isIncludedVars[i][j] ≠ isIncludedVars[j][i]}
 *       (exactly one of the two orderings holds — redundant but helps propagation)</li>
 * </ul>
 *
 * <h2>Global sweep propagation</h2>
 * <p>The {@link #propagate()} method, triggered on any domain change of the position
 * variables, set variables, or interval bounds, runs a sweep algorithm for each interval {@code i}:
 * <ol>
 *   <li>Collect the mandatory and possible predecessors of {@code i} from {@code befores[i]}.</li>
 *   <li>Compute an <em>earliest completion time profile</em> {@code ect[0..k]} by scheduling
 *       all predecessors using a variant of Jackson's rule (SR-ECT sweep, implemented with
 *       two priority queues). {@code ect[p]} is the earliest time at which the p-th
 *       predecessor can complete.</li>
 *   <li>Compute a <em>latest completion time profile</em> {@code lct[0..k]} symmetrically,
 *       scheduling predecessors backward from their latest end times.</li>
 *   <li>For each candidate position {@code p} of interval {@code i}:
 *     <ul>
 *       <li>Earliest start: {@code est = max(ect[p] + transitionAdjustment, startMin(i))},
 *           where {@code transitionAdjustment} accounts for the minimum transition times
 *           between the p predecessors and between the last predecessor and i.</li>
 *       <li>Latest start: {@code lst = min(lct[p] - transitionAdjustment - lengthMin(i), startMax(i))}.</li>
 *       <li>If {@code est > startMax(i)} or {@code lst < startMin(i)}, position p is removed
 *           from {@code posOfInterval[i]}.</li>
 *       <li>Otherwise, update {@code startMin(i) := min(est)} and {@code startMax(i) := max(lst)}.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>The transition time adjustment uses the global minimum transition {@code minTransition}
 * (minimum over all distinct pairs) as a lower bound for inter-predecessor gaps, and
 * {@code minTransToI} (minimum transition from any possible predecessor to i) for the
 * gap between the last predecessor and i. This is a conservative relaxation that never
 * removes feasible solutions while still providing useful pruning.
 *
 * <h2>Complexity</h2>
 * <p>Each propagation call is O(n² log n): for each of the n intervals, the sweep
 * algorithm runs in O(k log k) where k is the number of predecessors, and the position
 * check is O(n). The O(n²) {@link IsEndBeforeStartWithTransition} constraints provide
 * event-driven pairwise channeling and are only woken up when the relevant variables change.
 *
 * @see NoOverlapWithPositionDecomposition
 * @see InversePerm
 * @see NoOverlap
 * @see IsEndBeforeStartWithTransition
 */
public class NoOverlapWithPosition extends AbstractCPConstraint {

    /**
     * Number of intervals.
     */
    public final int n;
    /**
     * Intervals to be sequenced.
     */
    public final CPIntervalVar[] intervals;
    /**
     * {@code posOfInterval[i]} is the position of interval i in the sequence.
     */
    public final CPIntVar[] posOfInterval;
    /**
     * {@code intervalInPos[p]} is the interval at position p in the sequence.
     */
    public final CPIntVar[] intervalInPos;

    private final int[][] minTransitions;

    // --- Set-variable model ------------------------------------------------

    /**
     * {@code befores[i]} = set of intervals that precede interval i.
     */
    private CPSetVar[] befores;
    /**
     * {@code isIncludedVars[i][j]} = true iff interval i is before interval j.
     */
    private CPBoolVar[][] isIncludedVars;

    // --- Sweep algorithm working arrays ------------------------------------

    /** Earliest completion time profile: ect[p] = earliest completion of p-th predecessor. */
    private final int[] ect;
    /** Latest completion time profile: lct[p] = latest completion at position p. */
    private final int[] lct;
    /** Reusable array for the possible values of befores[i]. */
    private final int[] possibleTasks;
    /** Reusable array for the mandatory (included) values of befores[i]. */
    private final int[] mandatoryTasks;
    /** Merged array: possible + mandatory tasks before i (first nbPossible, then nbMandatory). */
    private final int[] beforeTasks;
    /** Current number of entries in {@link #beforeTasks}. */
    private int nbBeforeTasks;
    /** Reusable array for the possible positions of posOfInterval[i]. */
    private final int[] positions;
    /** {@code remainingDur[id]} = remaining processing time of task id during the sweep. */
    private final int[] remainingDur;
    /** {@code isMandatoryTask[id]} = true if task id is a mandatory predecessor of the current i. */
    private final boolean[] isMandatoryTask;

    // --- Priority queues for the sweep (indexed by interval id) ------------

    /** Queue keyed by startMin (ascending) — groups tasks by earliest start. */
    private final PriorityQueue<Integer> qByStart;
    /** Queue keyed by endMax (descending) — groups tasks by latest end. */
    private final PriorityQueue<Integer> qByEnd;
    /** Active queue for the forward sweep: mandatory tasks first, then by remaining duration. */
    private final PriorityQueue<Integer> qForward;
    /** Active queue for the backward sweep: mandatory tasks last, then by remaining duration. */
    private final PriorityQueue<Integer> qBackward;

    // --- Precomputed constants ---------------------------------------------

    /** Global minimum transition time over all distinct pairs. */
    private final int minTransition;
    /** {@code minTransTo[i]} = min over j≠i of trans[j][i] (static lower bound). */
    private final int[] minTransTo;

    /**
     * Creates a global permutation constraint with zero transition times.
     *
     * @param intervals        the interval variables to be sequenced (all must be present and share the same solver)
     * @param posOfInterval    position variables: {@code posOfInterval[i]} is the position of interval i (domain 0..n-1)
     * @param intervalInPos    inverse position variables: {@code intervalInPos[p]} is the interval at position p (domain 0..n-1)
     */
    public NoOverlapWithPosition(CPIntervalVar[] intervals, CPIntVar[] posOfInterval, CPIntVar[] intervalInPos) {
        this(intervals, posOfInterval, intervalInPos, new int[intervals.length][intervals.length]);
    }

    /**
     * Creates a global permutation constraint with minimum transition times between intervals.
     *
     * <p>The transition time matrix must satisfy the triangular inequality property,
     * i.e., for all i, j, k: {@code minTransition[i][j] <= minTransition[i][k] + minTransition[k][j]}.
     *
     * @param intervals        the interval variables to be sequenced (all must be present, same solver)
     * @param posOfInterval    position variables: {@code posOfInterval[i]} is the position of interval i (domain 0..n-1)
     * @param intervalInPos    inverse position variables: {@code intervalInPos[p]} is the interval at position p (domain 0..n-1)
     * @param minTransition    n×n matrix where {@code minTransition[i][j]} is the minimum transition time
     *                         from interval i to interval j
     * @throws AssertionError if the matrix dimensions don't match or triangular inequality is violated
     */
    public NoOverlapWithPosition(CPIntervalVar[] intervals, CPIntVar[] posOfInterval, CPIntVar[] intervalInPos, int[][] minTransition) {
        super(intervals[0].getSolver());
        assert posOfInterval.length == intervals.length;
        assert intervalInPos.length == intervals.length;
        assert minTransition.length == intervals.length;
        assert minTransition[0].length == intervals.length;
        DistanceMatrix.checkTriangularInequality(minTransition);
        this.intervals = intervals;
        this.n = intervals.length;
        for (int i = 0; i < n; i++) {
            assert intervals[i].isPresent();
        }
        this.minTransitions = minTransition;
        this.posOfInterval = posOfInterval;
        this.intervalInPos = intervalInPos;

        this.befores = new CPSetVar[n];
        this.isIncludedVars = new CPBoolVar[n][n];
        this.ect = new int[n];
        this.lct = new int[n];
        this.possibleTasks = new int[n];
        this.mandatoryTasks = new int[n];
        this.beforeTasks = new int[n];
        this.positions = new int[n];
        this.remainingDur = new int[n];
        this.isMandatoryTask = new boolean[n];

        // Compute global minimum transition and per-interval minimum transition to/from
        int minT = Integer.MAX_VALUE;
        this.minTransTo = new int[n];
        for (int i = 0; i < n; i++) {
            int minToI = Integer.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    minT = Math.min(minT, minTransition[i][j]);
                    minToI = Math.min(minToI, minTransition[j][i]);
                }
            }
            this.minTransTo[i] = (minToI == Integer.MAX_VALUE) ? 0 : minToI;
        }
        this.minTransition = (minT == Integer.MAX_VALUE) ? 0 : minT;

        // Priority queues operate on interval indices (int) to avoid HashMap lookups
        this.qByStart = new PriorityQueue<>((i1, i2) -> Integer.compare(intervals[i1].startMin(), intervals[i2].startMin()));
        this.qByEnd = new PriorityQueue<>((i1, i2) -> Integer.compare(intervals[i2].endMax(), intervals[i1].endMax()));
        this.qForward = new PriorityQueue<>((i1, i2) -> {
            // Mandatory tasks are scheduled first (they must be before i)
            if (isMandatoryTask[i1] && !isMandatoryTask[i2]) return -1;
            if (!isMandatoryTask[i1] && isMandatoryTask[i2]) return 1;
            return Integer.compare(remainingDur[i1], remainingDur[i2]);
        });
        this.qBackward = new PriorityQueue<>((i1, i2) -> {
            // Mandatory tasks are scheduled last (they must be before i)
            if (isMandatoryTask[i1] && !isMandatoryTask[i2]) return 1;
            if (!isMandatoryTask[i1] && isMandatoryTask[i2]) return -1;
            return Integer.compare(remainingDur[i1], remainingDur[i2]);
        });
    }

    // -----------------------------------------------------------------------
    //  Constraint posting
    // -----------------------------------------------------------------------

    @Override
    public void post() {
        CPSolver cp = getSolver();

        // 1) Position channeling + global no-overlap
        cp.post(new InversePerm(posOfInterval, intervalInPos));
        cp.post(new NoOverlap(intervals));

        // 2) Set-variable model: befores[i] and its channelings
        for (int i = 0; i < n; i++) {
            posOfInterval[i].removeBelow(0);
            posOfInterval[i].removeAbove(n - 1);
            befores[i] = new CPSetVarImpl(cp, n);
            befores[i].exclude(i);                     // i cannot be before itself
            befores[i].propagateOnDomainChange(this);
            posOfInterval[i].propagateOnDomainChange(this);
            intervals[i].propagateOnChange(this);
            // position of i = number of intervals before i
            cp.post(CPFactory.eq(posOfInterval[i], befores[i].card()));
        }

        // 3) Pairwise channeling: ordering boolean <-> set inclusion <-> position <->
        //    temporal ordering with transition times
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;

                isIncludedVars[i][j] = CPFactory.makeBoolVar(cp);
                // i in befores[j]  <=>  isIncludedVars[i][j]
                cp.post(new IsIncluded(isIncludedVars[i][j], befores[j], i));
                // befores[i] ⊆ befores[j]  <=>  isIncludedVars[i][j]  (transitivity)
                CPBoolVar biSubBj = CPFactory.makeBoolVar(cp);
                cp.post(new IsSubset(biSubBj, befores[i], befores[j]));
                cp.post(CPFactory.eq(isIncludedVars[i][j], biSubBj));
                // pos[i] < pos[j]  <=>  isIncludedVars[i][j]
                cp.post(CPFactory.eq(isIncludedVars[i][j],
                        CPFactory.isLt(posOfInterval[i], posOfInterval[j])));
                // end(i) + trans[i][j] <= start(j)  <=>  isIncludedVars[i][j]
                cp.post(new IsEndBeforeStartWithTransition(
                        isIncludedVars[i][j], intervals[i], intervals[j],
                        minTransitions[i][j], minTransitions[j][i]));
            }
        }

        // 4) Exactly one of (i before j) / (j before i) holds
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                cp.post(CPFactory.neq(isIncludedVars[i][j], isIncludedVars[j][i]));
            }
        }

        propagate();
    }

    // -----------------------------------------------------------------------
    //  Global propagation: sweep-based position + start/end pruning
    // -----------------------------------------------------------------------

    @Override
    public void propagate() {
        for (int i = 0; i < n; i++) {
            // Gather predecessors of i from befores[i] (possible + mandatory)
            int nbPossible = befores[i].fillPossible(possibleTasks);
            int nbMandatory = befores[i].fillIncluded(mandatoryTasks);
            nbBeforeTasks = nbPossible + nbMandatory;

            // Merge into beforeTasks and mark mandatory ones
            java.util.Arrays.fill(isMandatoryTask, 0, n, false);
            for (int j = 0; j < nbPossible; j++) {
                beforeTasks[j] = possibleTasks[j];
            }
            for (int j = 0; j < nbMandatory; j++) {
                int id = mandatoryTasks[j];
                beforeTasks[nbPossible + j] = id;
                isMandatoryTask[id] = true;
            }

            // Compute earliest/latest completion time profiles
            computeEarliestCompletionTimes();
            computeLatestCompletionTimes();

            // Minimum transition from any possible predecessor to i
            // (computed from the beforeTasks array, not a full O(n) scan)
            int minTransToI = minTransTo[i];
            for (int j = 0; j < nbBeforeTasks; j++) {
                int taskId = beforeTasks[j];
                if (minTransitions[taskId][i] < minTransToI) {
                    minTransToI = minTransitions[taskId][i];
                }
            }

            // Evaluate each candidate position p for interval i
            int remainingPos = posOfInterval[i].fillArray(positions);
            int minEst = Integer.MAX_VALUE;
            int maxLst = Integer.MIN_VALUE;
            int startMinI = intervals[i].startMin();
            int startMaxI = intervals[i].startMax();
            int lenMinI = intervals[i].lengthMin();

            for (int j = 0; j < remainingPos; j++) {
                int p = positions[j];
                // Earliest start at position p:
                //   p predecessors scheduled back-to-back, each separated by >= minTransition,
                //   plus a transition >= minTransToI from the last predecessor to i.
                int transBefore = (p > 0) ? (p - 1) * minTransition + minTransToI : 0;
                int est = Math.max(ect[p] + transBefore, startMinI);

                // Latest start at position p:
                //   lct[p] is the latest completion at position p (from the backward sweep).
                //   Subtract the same transition lower bound for the predecessors.
                int adjustedLct = lct[p] - transBefore;
                int lst = Math.min(adjustedLct - lenMinI, startMaxI);

                if (lst < startMinI || est > startMaxI) {
                    // Position p is infeasible for interval i
                    posOfInterval[i].remove(p);
                } else {
                    minEst = Math.min(minEst, est);
                    maxLst = Math.max(maxLst, lst);
                }
            }
            if (remainingPos > 0) {
                intervals[i].setStartMin(minEst);
                intervals[i].setStartMax(maxLst);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Forward sweep: earliest completion time profile (Jackson's rule)
    // -----------------------------------------------------------------------

    /**
     * Computes {@code ect[1..k]} where {@code ect[p]} is the earliest completion time
     * of the p-th task when scheduling all predecessors of the current interval i
     * using a variant of Jackson's rule (Shortest Remaining Processing Time first,
     * with mandatory tasks prioritised).
     *
     * <p>Algorithm: two-queue sweep. {@code qByStart} releases tasks by earliest start;
     * {@code qForward} selects the next task to execute by SRPT (mandatory first).
     * When a task completes, {@code ect[++p]} records the completion time.
     */
    private void computeEarliestCompletionTimes() {
        qByStart.clear();
        qForward.clear();

        for (int j = 0; j < nbBeforeTasks; j++) {
            int id = beforeTasks[j];
            remainingDur[id] = intervals[id].lengthMin();
            qByStart.add(id);
        }

        int p = 0;
        while (!qByStart.isEmpty()) {
            int id = qByStart.poll();
            int time = intervals[id].startMin();
            qForward.add(id);

            // Release all tasks with the same earliest start
            while (!qByStart.isEmpty() && intervals[qByStart.peek()].startMin() == time) {
                qForward.add(qByStart.poll());
            }

            // Execute tasks until the next release or all done
            while (!qForward.isEmpty() &&
                    (qByStart.isEmpty() || time + remainingDur[qForward.peek()] < intervals[qByStart.peek()].startMin())) {
                id = qForward.poll();
                time += remainingDur[id];
                remainingDur[id] = 0;
                p++;
                ect[p] = time;
            }
            // Preempt: partial execution of the current task until the next release
            if (!qForward.isEmpty()) {
                id = qForward.poll();
                int nextStart = intervals[qByStart.peek()].startMin();
                remainingDur[id] += time - nextStart;
                qForward.add(id);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Backward sweep: latest completion time profile
    // -----------------------------------------------------------------------

    /**
     * Computes {@code lct[0..k]} where {@code lct[p]} is the latest completion time
     * at position p when scheduling all predecessors backward from their latest end times.
     *
     * <p>Symmetric to {@link #computeEarliestCompletionTimes()}: {@code qByEnd} releases
     * tasks by latest end (descending); {@code qBackward} selects by SRPT (mandatory last).
     */
    private void computeLatestCompletionTimes() {
        qByEnd.clear();
        qBackward.clear();

        for (int j = 0; j < nbBeforeTasks; j++) {
            int id = beforeTasks[j];
            remainingDur[id] = intervals[id].lengthMin();
            qByEnd.add(id);
        }

        int p = nbBeforeTasks;
        lct[p] = HORIZON;

        while (!qByEnd.isEmpty()) {
            int id = qByEnd.poll();
            int time = intervals[id].endMax();
            qBackward.add(id);

            while (!qByEnd.isEmpty() && intervals[qByEnd.peek()].endMax() == time) {
                qBackward.add(qByEnd.poll());
            }

            while (!qBackward.isEmpty() &&
                    (qByEnd.isEmpty() || time - remainingDur[qBackward.peek()] >= intervals[qByEnd.peek()].endMax())) {
                id = qBackward.poll();
                time -= remainingDur[id];
                remainingDur[id] = 0;
                p--;
                lct[p] = time;
            }
            if (!qBackward.isEmpty()) {
                id = qBackward.poll();
                int nextEnd = intervals[qByEnd.peek()].endMax();
                remainingDur[id] -= time - nextEnd;
                qBackward.add(id);
            }
        }
    }
}
