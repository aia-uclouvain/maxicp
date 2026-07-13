/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.AllDifferentDC;
import org.maxicp.cp.engine.constraints.InversePerm;
import org.maxicp.cp.engine.constraints.setvar.IsIncluded;
import org.maxicp.cp.engine.constraints.setvar.IsSubset;
import org.maxicp.cp.engine.core.*;
import org.maxicp.util.Arrays;
import org.maxicp.util.algo.DistanceMatrix;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

import static org.maxicp.Constants.HORIZON;

/**
 * A global constraint that enforces a permutation (ordering) of interval variables
 * with minimum transition times, using set-variable-based propagation for stronger
 * filtering than the binary decomposition.
 *
 * <p>This constraint is an alternative to {@link Permutation} that uses a global
 * propagation algorithm based on the approach of {@code PositionBasedDisjunctive},
 * extended to handle transition times between consecutive intervals.
 *
 * <p>The constraint creates:
 * <ul>
 *   <li>{@code posOfInterval[i]} gives the position of interval i in the sequence</li>
 *   <li>{@code intervalInPos[p]} gives the interval at position p in the sequence</li>
 * </ul>
 *
 * <p>In addition to posting the inverse permutation channeling, a global no-overlap,
 * and pairwise binary transition-time constraints (as in {@link Permutation}),
 * this constraint also maintains set variables {@code befores[i]} representing the
 * set of intervals that must precede interval i, and uses a sweep-based algorithm
 * to compute earliest/latest completion time bounds for each possible position,
 * pruning both the position variables and the interval start/end bounds.
 *
 * @see Permutation
 * @see InversePerm
 * @see NoOverlap
 */
public class PermutationGlobal extends AbstractCPConstraint {

    /**
     * Number of intervals
     */
    public final int n;
    /**
     * Intervals
     */
    public final CPIntervalVar[] intervals;
    /**
     * posOfInterval[i] is the position of node i in the tour
     */
    public final CPIntVar[] posOfInterval;
    /**
     * intervalInPos[i] is the node at position i in the tour
     */
    public final CPIntVar[] intervalInPos;

    private final int[][] minTransitions;

    CPSolver cp;
    CPSetVar[] befores;
    CPBoolVar[][] isIncludedVars;
    int[] ect;
    int[] lct;
    int[] beforesValuesPossible;
    int[] beforesValuesMandatory;
    int[] positions;
    HashMap<CPIntervalVar, Integer> rd = new HashMap<>();
    HashSet<CPIntervalVar> mandatoryTasksSet = new HashSet<>();
    int[] beforeMandatoryTasks;
    int[] beforeMandatoryPossibleTasks;
    int nbBeforeMandatoryPossibleTasks;
    PriorityQueue<CPIntervalVar> q1Latest = new PriorityQueue<>((t1, t2) -> t2.endMax() - t1.endMax());
    PriorityQueue<CPIntervalVar> q1Earliest = new PriorityQueue<>(Comparator.comparingInt(CPIntervalVar::startMin));
    PriorityQueue<CPIntervalVar> q2Earliest = new PriorityQueue<>((t1, t2) -> {
        boolean t1Mandatory = mandatoryTasksSet.contains(t1);
        boolean t2Mandatory = mandatoryTasksSet.contains(t2);
        if (t1Mandatory && !t2Mandatory) return -1;
        else if (!t1Mandatory && t2Mandatory) return 1;
        return Integer.compare(rd.get(t1), rd.get(t2));
    });
    PriorityQueue<CPIntervalVar> q2Latest = new PriorityQueue<>((t1, t2) -> {
        boolean t1Mandatory = mandatoryTasksSet.contains(t1);
        boolean t2Mandatory = mandatoryTasksSet.contains(t2);
        if (t1Mandatory && !t2Mandatory) return 1;
        else if (!t1Mandatory && t2Mandatory) return -1;
        return Integer.compare(rd.get(t1), rd.get(t2));
    });

    // Minimum transition time between any two distinct intervals
    private final int minTransition;

    /**
     * Creates a global permutation constraint with zero transition times.
     *
     * @param intervals the interval variables to be sequenced (all intervals must belong to the same solver)
     */
    public PermutationGlobal(CPIntervalVar[] intervals) {
        this(intervals, new int[intervals.length][intervals.length]);
    }

    /**
     * Creates a global permutation constraint with minimum transition times between intervals.
     *
     * <p>The transition time matrix must satisfy the triangular inequality property,
     * i.e., for all i, j, k: {@code minTransition[i][j] <= minTransition[i][k] + minTransition[k][j]}.
     *
     * @param intervals      the interval variables to be sequenced (all intervals must belong to the same solver)
     * @param minTransition  a square matrix where {@code minTransition[i][j]} is the minimum transition time
     *                       from interval i to interval j. Must have dimensions n×n where n is the number of intervals.
     * @throws AssertionError if the matrix dimensions don't match the number of intervals
     * @throws AssertionError if the triangular inequality is violated
     */
    public PermutationGlobal(CPIntervalVar[] intervals, int[][] minTransition) {
        super(intervals[0].getSolver());
        assert minTransition.length == intervals.length;
        assert minTransition[0].length == intervals.length;
        DistanceMatrix.checkTriangularInequality(minTransition);
        this.intervals = intervals;
        this.n = intervals.length;
        for (int i = 0; i < n; i++) {
            assert intervals[i].isPresent();
        }
        this.minTransitions = minTransition;
        this.posOfInterval = CPFactory.makeIntVarArray(getSolver(), n, n);
        this.intervalInPos = CPFactory.makeIntVarArray(getSolver(), n, n);
        this.cp = getSolver();

        this.befores = new CPSetVar[n];
        this.ect = new int[n];
        this.lct = new int[n];
        this.beforesValuesPossible = new int[n];
        this.beforesValuesMandatory = new int[n];
        this.beforeMandatoryTasks = new int[n];
        this.beforeMandatoryPossibleTasks = new int[n];
        this.isIncludedVars = new CPBoolVar[n][n];
        this.positions = new int[n];

        // Compute global minimum transition time between distinct intervals
        int minT = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) minT = Math.min(minT, minTransition[i][j]);
            }
        }
        this.minTransition = (minT == Integer.MAX_VALUE) ? 0 : minT;
    }

    @Override
    public void post() {
        cp.post(new InversePerm(posOfInterval, intervalInPos));
        cp.post(new NoOverlap(intervals));

        for (int i = 0; i < n; i++) {
            posOfInterval[i].removeBelow(0);
            posOfInterval[i].removeAbove(n - 1);
            befores[i] = new CPSetVarImpl(cp, n);
            befores[i].exclude(i);
            befores[i].propagateOnDomainChange(this);
            posOfInterval[i].propagateOnDomainChange(this);
            intervals[i].propagateOnChange(this);
            cp.post(CPFactory.eq(posOfInterval[i], befores[i].card()));
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;

                isIncludedVars[i][j] = CPFactory.makeBoolVar(cp);
                CPBoolVar biSubBj = CPFactory.makeBoolVar(cp);
                cp.post(new IsIncluded(isIncludedVars[i][j], befores[j], i));
                cp.post(new IsSubset(biSubBj, befores[i], befores[j]));
                cp.post(CPFactory.eq(isIncludedVars[i][j], biSubBj));
                CPBoolVar piLessThanPj = CPFactory.isLt(posOfInterval[i], posOfInterval[j]);
                cp.post(CPFactory.eq(isIncludedVars[i][j], piLessThanPj));
                // Channel temporal ordering with transition times:
                // isIncludedVars[i][j] = true  <=> end(i) + trans[i][j] <= start(j)
                // isIncludedVars[i][j] = false <=> end(j) + trans[j][i] <= start(i)
                cp.post(new NoOverlapBinaryWithTransitionTime(
                        isIncludedVars[i][j], intervals[i], intervals[j],
                        minTransitions[i][j], minTransitions[j][i]));
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                cp.post(CPFactory.neq(isIncludedVars[i][j], isIncludedVars[j][i]));
            }
        }

        propagate();
    }

    @Override
    public void propagate() {
        for (int i = 0; i < n; i++) {
            int remainingPos = posOfInterval[i].fillArray(positions);
            int nbPossible = befores[i].fillPossible(beforeMandatoryPossibleTasks);
            int nbMandatory = befores[i].fillIncluded(beforeMandatoryTasks);
            nbBeforeMandatoryPossibleTasks = nbPossible + nbMandatory;
            mandatoryTasksSet.clear();
            for (int j = 0; j < nbMandatory; j++) {
                beforeMandatoryPossibleTasks[j + nbPossible] = beforeMandatoryTasks[j];
                mandatoryTasksSet.add(intervals[beforeMandatoryTasks[j]]);
            }

            boundEarliestCompletionTime();
            boundLatestCompletionTime();

            int minTransToI = getMinTransitionTo(i);

            int minEst = Integer.MAX_VALUE;
            int maxLst = Integer.MIN_VALUE;
            for (int j = 0; j < remainingPos; j++) {
                int p = positions[j];
                // Earliest start at position p:
                // p tasks before i need (p-1) transitions between them (each >= minTransition)
                // plus 1 transition from the last task to i (>= minTransToI)
                int transBefore = (p > 0) ? (p - 1) * minTransition + minTransToI : 0;
                int est = Math.max(ect[p] + transBefore, intervals[i].startMin());

                // Latest start at position p:
                // lct[p] is the latest completion at position p (without transitions)
                // The p tasks before i need (p-1) transitions between them (each >= minTransition)
                // plus 1 transition from the last task to i (>= minTransToI)
                int transBeforeLct = (p > 0) ? (p - 1) * minTransition + minTransToI : 0;
                int adjustedLct = lct[p] - transBeforeLct;
                int lst = Math.min(adjustedLct - intervals[i].lengthMin(), intervals[i].startMax());

                if (!(lst >= intervals[i].startMin() && est <= intervals[i].startMax())) {
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

    /**
     * Returns the minimum transition time from any task that could be before i to i.
     */
    private int getMinTransitionTo(int i) {
        int min = Integer.MAX_VALUE;
        for (int j = 0; j < n; j++) {
            if (j != i && !befores[i].isExcluded(j)) {
                min = Math.min(min, minTransitions[j][i]);
            }
        }
        return (min == Integer.MAX_VALUE) ? 0 : min;
    }

    public void boundEarliestCompletionTime() {
        rd.clear();
        q1Earliest.clear();
        q2Earliest.clear();

        int time;
        int p = 0;
        CPIntervalVar t;
        int id;
        for (int i = 0; i < nbBeforeMandatoryPossibleTasks; i++) {
            id = beforeMandatoryPossibleTasks[i];
            rd.put(intervals[id], intervals[id].lengthMin());
            q1Earliest.add(intervals[id]);
        }

        while (!q1Earliest.isEmpty()) {
            t = q1Earliest.poll();
            time = t.startMin();
            q2Earliest.add(t);

            while (!q1Earliest.isEmpty() && q1Earliest.peek().startMin() == time) {
                t = q1Earliest.poll();
                q2Earliest.add(t);
            }

            while (!q2Earliest.isEmpty() && (q1Earliest.isEmpty() || time + rd.get(q2Earliest.peek()) < q1Earliest.peek().startMin())) {
                t = q2Earliest.poll();
                time += rd.get(t);
                rd.put(t, 0);
                p = p + 1;
                ect[p] = time;
            }
            if (!q2Earliest.isEmpty()) {
                t = q2Earliest.poll();
                rd.put(t, rd.get(t) + time - q1Earliest.peek().startMin());
                q2Earliest.add(t);
            }
        }
    }

    public void boundLatestCompletionTime() {
        rd.clear();
        q1Latest.clear();
        q2Latest.clear();

        int p = nbBeforeMandatoryPossibleTasks;
        int time;
        CPIntervalVar t;

        int id;
        for (int i = 0; i < nbBeforeMandatoryPossibleTasks; i++) {
            id = beforeMandatoryPossibleTasks[i];
            rd.put(intervals[id], intervals[id].lengthMin());
            q1Latest.add(intervals[id]);
        }
        lct[p] = HORIZON;

        while (!q1Latest.isEmpty()) {
            t = q1Latest.poll();
            time = t.endMax();
            q2Latest.add(t);
            while (!q1Latest.isEmpty() && q1Latest.peek().endMax() == time) {
                t = q1Latest.poll();
                q2Latest.add(t);
            }
            while (!q2Latest.isEmpty() && (q1Latest.isEmpty() || time - rd.get(q2Latest.peek()) >= q1Latest.peek().endMax())) {
                t = q2Latest.poll();
                time -= rd.get(t);
                rd.put(t, 0);
                p = p - 1;
                lct[p] = time;
            }
            if (!q2Latest.isEmpty()) {
                t = q2Latest.poll();
                rd.put(t, rd.get(t) - time + q1Latest.peek().endMax());
                q2Latest.add(t);
            }
        }
    }

    /**
     * Creates and returns a variable representing the total transition cost of the sequence.
     *
     * @param transitionCost a square matrix where {@code transitionCost[i][j]} is the cost
     *                       of transitioning from interval i to interval j. Must have dimensions n×n.
     * @return a CPIntVar representing the total transition cost of the permutation
     */
    public CPIntVar transitionCost(int[][] transitionCost) {
        assert transitionCost != null;
        assert transitionCost.length == n;
        assert transitionCost[0].length == n;
        DistanceMatrix.checkTriangularInequality(transitionCost);
        int maxTransition = Arrays.max(transitionCost);
        CPIntVar[] transitionTimes = CPFactory.makeIntVarArray(getSolver(), n - 1, 0, maxTransition);
        for (int i = 0; i < n - 1; i++) {
            getSolver().post(CPFactory.eq(transitionTimes[i],
                    CPFactory.element(transitionCost, intervalInPos[i], intervalInPos[i + 1])));
        }
        return CPFactory.sum(transitionTimes);
    }
}
