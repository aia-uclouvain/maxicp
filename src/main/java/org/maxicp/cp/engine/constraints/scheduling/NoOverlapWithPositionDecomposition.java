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
import org.maxicp.util.algo.DistanceMatrix;

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
public class NoOverlapWithPositionDecomposition extends AbstractCPConstraint {

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
     * @param intervals        the interval variables to be sequenced (all intervals must belong to the same solver)
     * @param posOfInterval    position variables: {@code posOfInterval[i]} is the position of interval i (domain 0..n-1)
     * @param intervalInPos    inverse position variables: {@code intervalInPos[p]} is the interval at position p (domain 0..n-1)
     */
    public NoOverlapWithPositionDecomposition(CPIntervalVar[] intervals, CPIntVar[] posOfInterval, CPIntVar[] intervalInPos) {
        this(intervals, posOfInterval, intervalInPos, new int[intervals.length][intervals.length]);
    }

    /**
     * Creates a permutation constraint with minimum transition times between intervals.
     *
     * <p>The transition time matrix must satisfy the triangular inequality property,
     * i.e., for all i, j, k: {@code minTransition[i][j] <= minTransition[i][k] + minTransition[k][j]}.
     *
     * @param intervals        the interval variables to be sequenced (all intervals must belong to the same solver)
     * @param posOfInterval    position variables: {@code posOfInterval[i]} is the position of interval i (domain 0..n-1)
     * @param intervalInPos    inverse position variables: {@code intervalInPos[p]} is the interval at position p (domain 0..n-1)
     * @param minTransition    a square matrix where {@code minTransition[i][j]} is the minimum transition time
     *                         from interval i to interval j. Must have dimensions n×n where n is the number of intervals.
     * @throws AssertionError if the matrix dimensions don't match the number of intervals
     * @throws AssertionError if the triangular inequality is violated
     */
    public NoOverlapWithPositionDecomposition(CPIntervalVar[] intervals, CPIntVar[] posOfInterval, CPIntVar[] intervalInPos, int[][] minTransition) {
        super(intervals[0].getSolver());
        assert posOfInterval.length == intervals.length;
        assert intervalInPos.length == intervals.length;
        assert minTransition.length == intervals.length;
        assert minTransition[0].length == intervals.length;
        DistanceMatrix.checkTriangularInequality(minTransition);
        this.intervals = intervals;
        this.n = intervals.length;
        // verify that all the intervals are mandatory
        for (int i = 0; i < n; i++) {
            assert intervals[i].isPresent();
        }
        this.posOfInterval = posOfInterval;
        this.intervalInPos = intervalInPos;
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
}