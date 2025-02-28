package org.maxicp.modeling.constraints.seqvar;

import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class TransitionTimes implements Constraint {

    public final SeqVar seqVar;
    public final IntExpression[] time;
    public final int[][] dist;
    public final int[] serviceTime;

    /**
     * Constraint linking nodes with time windows, updating the time windows of nodes depending on when they are visited,
     * based on a distance matrix and service time.
     * The sequence can await before visiting a node until its time window becomes available.
     *
     * @param seqVar sequence where the visit time of the nodes must be updated
     * @param time time window of each node. {@code time[node]} gives the time at which node can be visited
     * @param dist distance matrix between nodes
     * @param serviceTime duration of each node
     */
    public TransitionTimes(SeqVar seqVar, IntExpression[] time, int[][] dist, int[] serviceTime) {
        this.seqVar = seqVar;
        this.time = time;
        this.dist = dist;
        this.serviceTime = serviceTime;
        // TODO check triangular inequality with service time
    }

    /**
     * Constraint linking nodes with time windows, updating the time windows of nodes depending on when they are visited,
     * based on a distance matrix and service time.
     * The sequence can await before visiting a node until its time window becomes available.
     *
     * @param seqVar sequence where the visit time of the nodes must be updated
     * @param time time window of each node. {@code time[node]} gives the time at which node can be visited
     * @param dist distance matrix between nodes
     */
    public TransitionTimes(SeqVar seqVar, IntExpression[] time, int[][] dist) {
        this(seqVar, time, dist, null);
    }

    @Override
    public Collection<? extends Expression> scope() {
        List<Expression> scope = new ArrayList<>();
        scope.add(seqVar);
        scope.addAll(Arrays.asList(time));
        return scope;
    }
}
