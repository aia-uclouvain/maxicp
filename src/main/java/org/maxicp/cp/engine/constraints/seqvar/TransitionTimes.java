/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.state.State;

import java.util.Optional;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

/**
 * Links a {@link CPSeqVar} with time windows, enforcing that visits happen during valid timeframes.
 */
public class TransitionTimes extends AbstractCPConstraint {

    private final CPSeqVar seqVar;              // sequence ordering the visits of nodes
    private final CPIntVar[] time;              // time window of each node
    private final int[][] dist;                 // distance between each pair of nodes
    private Optional<int[]> serviceTime;        // time to process each node

    private final int[] nodes;                  // used for fill operations over the nodes
    private final int[] preds;                  // used for fill operations over the pred of nodes

    private State<Boolean> masterConstraintPropagating; // set to true whenever the constraint begins to be propagated

    /**
     * Links visits of nodes within a {@link CPSeqVar} to given time windows {@link CPIntVar}.
     * The nodes belonging to the sequence must be visited during their time window.
     * The triangular inequality must be satisfied.
     *
     * @param seqVar sequence visiting the nodes
     * @param time time windows of the nodes
     * @param dist distance matrix between the nodes
     */
    public TransitionTimes(CPSeqVar seqVar, CPIntVar[] time, int[][] dist) {
        this(seqVar, time, dist, Optional.empty());
    }

    /**
     * Links visits of nodes within a {@link CPSeqVar} to given time windows {@link CPIntVar}.
     * The nodes belonging to the sequence must be visited during their time window.
     * The triangular inequality must be satisfied.
     *
     * @param seqVar sequence visiting the nodes
     * @param time time windows of the nodes
     * @param dist distance matrix between the nodes
     * @param serviceTime processing time of each node
     */
    public TransitionTimes(CPSeqVar seqVar, CPIntVar[] time, int[][] dist, int[] serviceTime) {
        this(seqVar, time, dist, serviceTime == null ? Optional.empty() : Optional.of(serviceTime));
    }

    /**
     * Links visits of nodes within a {@link CPSeqVar} to given time windows {@link CPIntVar}.
     * The nodes belonging to the sequence must be visited during their time window.
     * The triangular inequality must be satisfied.
     *
     * @param seqVar sequence visiting the nodes
     * @param time time windows of the nodes
     * @param dist distance matrix between the nodes
     * @param serviceTime processing time of each node
     */
    public TransitionTimes(CPSeqVar seqVar, CPIntVar[] time, int[][] dist, Optional<int[]> serviceTime) {
        super(seqVar.getSolver());
        if (serviceTime == null)
            serviceTime = Optional.empty();
        assert time.length >= seqVar.nNode();
        assert dist.length >= seqVar.nNode();
        for (int[] d: dist) {
            assert d.length >= seqVar.nNode();
        }
        assert serviceTime.isEmpty() || serviceTime.get().length >= seqVar.nNode();
        this.seqVar = seqVar;
        this.time = time;
        this.dist = dist;
        this.serviceTime = serviceTime;
        this.nodes = new int[seqVar.nNode()];
        this.preds = new int[seqVar.nNode()];
        masterConstraintPropagating = getSolver().getStateManager().makeStateRef(false);
        checkTriangularInequality(dist);
    }

    private void checkTriangularInequality(int[][] dist) {
        for (int i = 0 ; i < dist.length ; i++) {
            for (int j = 0 ; j < dist[i].length ; j++) {
                int smallestDist = dist[i][j];
                for (int k = 0 ; k < dist.length ; k++) {
                    int distWithDetour = dist[i][k] + serviceTime(k) + dist[k][j];
                    if (distWithDetour < smallestDist) {
                        System.err.println("[WARNING]: triangular inequality not respected with distance matrix and service time");
                        System.err.printf("[WARNING]: dist[%d][%d] + duration[%d] + dist[%d][%d] < dist[%d][%d] (%d + %d + %d < %d)%n", i, k, k, k, j, i, j,
                                dist[i][k], serviceTime(k), dist[k][j], dist[i][j]);
                        System.err.println("[WARNING]: this might remove some solutions");
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void post() {
        // first trivial filtering over insertions, taking an infinite detour length as valid
        int nNodes = seqVar.fillNode(nodes, INSERTABLE);
        for (int i = 0 ; i < nNodes ; ++i) {
            int node = nodes[i];
            filterInsertsAndPossiblyTW(node);
        }
        // full filtering
        propagate();
        // register the propagation
        nNodes = seqVar.fillNode(nodes, INSERTABLE);
        for (int i = 0 ; i < nNodes ; ++i) {
            int node = nodes[i];
            new NodeTimeWindow(node).post();
        }
        int nMember = seqVar.fillNode(nodes, MEMBER);
        for (int i = 0 ; i < nMember ; ++i) {
            int node = nodes[i];
            time[node].propagateOnBoundChange(this);
        }
        seqVar.propagateOnInsert(this);
        seqVar.propagateOnFix(this);
    }

    @Override
    public void propagate() {
        // the constraint is being propagated
        masterConstraintPropagating.setValue(true);
        seqVar.fillNode(nodes, MEMBER_ORDERED);
        // update the time window
        updateTWForward();
        updateTWBackward();
        // the time windows have been updated, filters the predecessors
        filterInsertsAndPossiblyTW();
        // end of propagation
        masterConstraintPropagating.setValue(false);
    }

    /**
     * Updates the minimum start time of the time windows for member nodes.
     * The array {@link TransitionTimes#nodes} must be filled with the nodes, in order of appearance.
     */
    private void updateTWForward() {
        int nMember = seqVar.nNode(MEMBER_ORDERED);
        int pred = nodes[0];
        int predTime = time[pred].min() + serviceTime(pred);
        for (int i = 1 ; i < nMember ; ++i) {
            int current = nodes[i];
            predTime += dist[pred][current];
            time[current].removeBelow(predTime);
            // waiting at a node is allowed
            predTime = Math.max(predTime, time[current].min()) + serviceTime(current);
            pred = current;
        }
    }

    /**
     * Updates the maximum start time of the time windows for member nodes.
     * The array {@link TransitionTimes#nodes} must be filled with the nodes, in order of appearance.
     */
    private void updateTWBackward() {
        int n = seqVar.nNode(MEMBER_ORDERED);
        int succ = nodes[n-1];
        int succTime = time[succ].max();
        for (int i = n - 2 ; i >= 0 ; --i) {
            int current = nodes[i];
            succTime -= dist[current][succ] + serviceTime(current);
            time[current].removeAbove(succTime);
            succTime = time[current].max();

            succ = current;
        }
    }

    /**
     * Filter an edge from the sequence if it would violate the time windows.
     * @param pred origin of the edge
     * @param node destination of the edge
     * @return true if the edge has been removed
     */
    private boolean filterInsert(int pred, int node) {
        int succ = seqVar.memberAfter(pred);
        int timeReachingNode = time[pred].min() + serviceTime(pred) + dist[pred][node];
        if (timeReachingNode > time[node].max()) { // check that pred -> current is feasible
            seqVar.notBetween(pred, node, succ);
            return true;
        } else { // check that current -> succ is feasible
            int timeDeparture = Math.max(timeReachingNode, time[node].min());
            if (timeDeparture + serviceTime(node) + dist[node][succ] > time[succ].max()) {
                // The detour pred->node->succ takes too much time.
                // Because of triangular inequality, there is no way to get a better result by inserting some node
                // between pred->node: this would only add a longer delay, the edge is still invalid.
                seqVar.notBetween(pred, node, succ);
                return true;
            }
        }
        return false;

    }

    /**
     * Filter the predecessors for all the insertable nodes
     * - if the node is required, filters all predecessors and updates the time window of the node
     * - otherwise, filters only the member predecessors
     */
    private void filterInsertsAndPossiblyTW() {
        int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
        for (int i = 0 ; i < nInsertable ; ++i) {
            int node = nodes[i];
            filterInsertsAndPossiblyTW(node);
        }
    }

    /**
     * Filter the insertions for an insertable node
     * - if the node is required, filters all insertions and updates the time window of the node
     * - otherwise, filters only the member predecessors
     */
    private void filterInsertsAndPossiblyTW(int node) {
        int nPred = seqVar.fillInsert(node, preds);
        if (seqVar.isNode(node, REQUIRED)) {
            // update the insertions as well as the time window
            int earliestArrival = Integer.MAX_VALUE;
            int latestDeparture = Integer.MIN_VALUE;
            for (int i = 0; i < nPred; ++i) {
                int pred = preds[i];
                if (!filterInsert(pred, node)) {
                    // if the edge is valid, it can be used to decide the min and max departure time
                    int arrival = time[pred].min() + serviceTime(pred) + dist[pred][node]; // min departure time
                    earliestArrival = Math.min(earliestArrival, arrival);
                    int succ = seqVar.memberAfter(pred);
                    int departure = time[succ].max() - serviceTime(node) - dist[node][succ]; // max departure time
                    latestDeparture = Math.max(latestDeparture, departure);
                }
            }
            if (!seqVar.isNode(node, MEMBER)) {
                // if the node has not become inserted because of the insertions removal, update its time window
                time[node].removeBelow(earliestArrival);
                time[node].removeAbove(latestDeparture);
            }
        } else {
            for (int i = 0; i < nPred; ++i) {
                int pred = preds[i];
                filterInsert(pred, node);
            }
        }
    }

    /**
     * Gives the service time at a given node
     * @param node node whose service time must be known
     * @return service time at the node
     */
    private int serviceTime(int node) {
        return serviceTime.isPresent() ? serviceTime.get()[node] : 0;
    }

    /**
     * Handles the update over a node.
     * The updates here are faster than by calling the full propagation from {@link TransitionTimes}
     * but are only valid depending on certain criterion.
     * This can be viewed as handling incremental updates
     */
    private class NodeTimeWindow extends AbstractCPConstraint {

        private final int node;

        public NodeTimeWindow(int node) {
            super(TransitionTimes.this.getSolver());
            this.node = node;
        }

        @Override
        public boolean isActive() {
            // the incremental update should only be done if
            // - the TransitionTimes constraint is not being propagated at the moment
            // - the filtering can do something over the time window of the node / its state: the node must not be excluded
            // - the TransitionTimes constraint is not scheduled at the moment
            // - the TransitionTimes constraint is still active
            return !masterConstraintPropagating.value()
                    && !seqVar.isNode(node, EXCLUDED)
                    && !TransitionTimes.this.isScheduled()
                    && TransitionTimes.this.isActive();
        }

        @Override
        public void post() {
            if (!seqVar.isNode(node, EXCLUDED)) {
                time[node].propagateOnBoundChange(this);
                seqVar.getNodeVar(node).propagateOnRequire(this);
            }
        }

        @Override
        public void propagate() {
            if (seqVar.isNode(node, MEMBER) && !TransitionTimes.this.isScheduled()) {
                // if the node is a member, a lot of things must happen. It's better to update the whole constraint
                getSolver().schedule(TransitionTimes.this);
            } else if (seqVar.isNode(node, INSERTABLE)) {
                filterInsertsAndPossiblyTW(node);
            }
        }
    }

}
