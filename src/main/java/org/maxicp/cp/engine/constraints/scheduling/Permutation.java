/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.InversePerm;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.modeling.IntVar;
import org.maxicp.util.Arrays;
import org.maxicp.util.algo.DistanceMatrix;

import java.util.ArrayList;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.cp.CPFactory.eq;

/**
 * A constraint that enforces a permutation (ordering) of interval variables,
 * typically used for routing/scheduling problems such as TSP or job sequencing.
 *
 * <p>This constraint models a sequence of visits where:
 * <ul>
 *   <li>Each interval represents a visit/task to be scheduled</li>
 *   <li>{@code posOfInterval[i]} gives the position of interval i in the sequence</li>
 *   <li>{@code intervalInPos[p]} gives the interval at position p in the sequence</li>
 * </ul>
 *
 * <p>The constraint enforces:
 * <ul>
 *   <li>Inverse permutation relationship between posOfInterval and intervalInPos</li>
 *   <li>No overlap between intervals</li>
 *   <li>Minimum transition times between consecutive intervals (if specified)</li>
 * </ul>
 *
 * @see InversePerm
 * @see NoOverlapBinaryWithTransitionTime
 */
public class Permutation extends AbstractCPConstraint {

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
    public final CPIntVar [] intervalInPos;

    private int [][] minTransitions;

    /**
     * Creates a permutation constraint with zero transition times.
     *
     * @param intervals the interval variables to be sequenced (all intervals must belong to the same solver)
     */
    public Permutation(CPIntervalVar[] intervals) {
        this(intervals,new int[intervals.length][intervals.length]);
    }

    /**
     * Creates a permutation constraint with minimum transition times between intervals.
     *
     * <p>The transition time matrix must satisfy the triangular inequality property,
     * i.e., for all i, j, k: {@code minTransition[i][j] <= minTransition[i][k] + minTransition[k][j]}.
     *
     * @param intervals the interval variables to be sequenced (all intervals must belong to the same solver)
     * @param minTransition a square matrix where {@code minTransition[i][j]} is the minimum transition time
     *                      from interval i to interval j. Must have dimensions n×n where n is the number of intervals.
     * @throws AssertionError if the matrix dimensions don't match the number of intervals
     * @throws AssertionError if the triangular inequality is violated
     */
    public Permutation(CPIntervalVar[] intervals, int [][] minTransition) {
        super(intervals[0].getSolver());
        assert minTransition.length == intervals.length;
        assert minTransition[0].length == intervals.length;
        DistanceMatrix.checkTriangularInequality(minTransition);
        this.intervals = intervals;
        this.n = intervals.length;
        // verify that all the intervals are mandatory
        for (int i = 0; i < n; i++) {
            assert intervals[i].isPresent();
        }
        posOfInterval = CPFactory.makeIntVarArray(getSolver(), n,n);
        intervalInPos = CPFactory.makeIntVarArray(getSolver(), n,n);
        this.minTransitions = minTransition;
    }



    @Override
    public void post() {
        // channeling between positionOfNode and taskInPosition positionOfNode[i] = p <=> taskInPosition[p] = i
        getSolver().post(new InversePerm(posOfInterval, intervalInPos)); // this also post the allDifferent constraints
        // no overlap between visits
        getSolver().post(noOverlap(intervals));
        // noOverlap with transition times
        for (int i = 0; i < n-1; i++) {
            for (int j = i+1; j < n; j++) {
                // order = 1 <=> positionOfNode[i] < positionOfNode[j]
                // order = 0 <=> positionOfNode[j] < positionOfNode[i]
                CPBoolVar order = CPFactory.strictOrder(posOfInterval[i], posOfInterval[j]);
                // if order = 1 then visits[i] before visits[j] with transition time instance.distMatrix[i][j]
                // if order = 0 then visits[j] before visits[i] with transition time instance
                getSolver().post(new NoOverlapBinaryWithTransitionTime(order, intervals[i], intervals[j], minTransitions[i][j], minTransitions[j][i]));
            }
        }
    }

    /**
     * Creates and returns a variable representing the total transition cost of the sequence.
     *
     * <p>The transition cost is computed as the sum of transition times between consecutive
     * intervals in the sequence: {@code sum(transitionCost[intervalInPos[i]][intervalInPos[i+1]])}
     * for i from 0 to n-2.
     *
     * <p>The transition cost matrix must satisfy the triangular inequality property.
     *
     * @param transitionCost a square matrix where {@code transitionCost[i][j]} is the cost
     *                       of transitioning from interval i to interval j. Must have dimensions n×n.
     * @return a CPIntVar representing the total transition cost of the permutation
     * @throws AssertionError if the matrix is null or dimensions don't match
     * @throws AssertionError if the triangular inequality is violated
     */
    public CPIntVar transitionCost(int [][] transitionCost) {
        assert(transitionCost != null);
        assert(transitionCost.length == n);
        assert(transitionCost[0].length == n);
        DistanceMatrix.checkTriangularInequality(transitionCost);
        int maxTransition = Arrays.max(transitionCost);
        // transition times and objective
        CPIntVar[] transitionTimes = makeIntVarArray(getSolver(), n-1, 0, maxTransition);
        for (int i = 0; i < n-1; i++) {
            getSolver().post(eq(transitionTimes[i],
                    CPFactory.element(transitionCost, intervalInPos[i], intervalInPos[i+1])));
        }
        CPIntVar totTransition = CPFactory.sum(transitionTimes);
        return totTransition;
    }
}