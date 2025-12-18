/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.util.algo.LayeredGraph;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;

/**
 * Bound Consistency filtering for the NoOverlap constraint
 * @author Pierre Schaus
 */
public class NoOverlapBCLeftToRight {

    public enum Outcome {
        NO_CHANGE, CHANGE, INCONSISTENCY
    }

    public final int[] startMin, endMax;
    private final int[] startMinNew, startMax, duration, endMin;
    int n;

    public NoOverlapBCLeftToRight(int nMax) {
        startMin = new int[nMax];
        startMinNew = new int[nMax];
        startMax = new int[nMax];
        duration = new int[nMax];
        endMin = new int[nMax];
        endMax = new int[nMax];
    }

    private record State(int t, int some) {}

    /**
     *
     * @param startMin the minimum start time of each activity
     * @param duration the duration of each activity
     * @param endMax   the maximum end time of each activity
     * @param n        a number between 0 and startMin.length-1, is the number of activities to consider (prefix),
     *                 The other ones are just ignored
     * @return the outcome of the filtering, either NO_CHANGE, CHANGE or INCONSISTENCY.
     * If a change is detected, the time windows (startMin and endMax) are reduced.
     */
    public Outcome filter(int[] startMin, int[] duration, int[] endMax, int n) {
        update(startMin, duration, endMax, n);

        LayeredGraph<State> graph = new LayeredGraph(n+1);

        // root node
        graph.startLayer(0);
        graph.getOrCreateNode(new State(Integer.MIN_VALUE, 0));
        graph.endLayer(0);

        for (int i = 0; i < n; i++) {
            graph.startLayer(i + 1);
            for (int nodeId : graph.nodesInLayer(i)) {
                State state = graph.getState(nodeId);
                for (int j = 0; j < n; j++) {
                    if (!contains(state.some, j)) {
                        int newT = Math.max(state.t, startMin[j]) + duration[j];
                        if (newT <= endMax[j]) {
                            int newSome = state.some | (1 << j);
                            State newState = new State(newT, newSome);
                            int newNodeId = graph.getOrCreateNode(newState);
                            graph.addEdge(nodeId, newNodeId, j);
                        }
                    }
                }
            }
            graph.endLayer(i + 1);
            if (graph.layerSize(i+1) == 0) {
                return Outcome.INCONSISTENCY; // no feasible schedule
            }
        }

        // reverse BFS from the last layer
        Set<Integer> currentLayerToVisit = new HashSet<>();
        Set<Integer> previousLayerToVisit = new HashSet<>();
        for (int nodeId : graph.nodesInLayer(n)) {
            currentLayerToVisit.add(nodeId);
        }
        for (int i = n; i > 0; i--) {
            previousLayerToVisit.clear();
            for (int nodeId : currentLayerToVisit) {
                LayeredGraph.EdgeList edgeList = graph.predecessors(nodeId);
                for (int e = 0; e < edgeList.size(); e++) {
                    int act = edgeList.label(e);
                    State predState = graph.getState(edgeList.node(e));
                    this.startMinNew[act] = Math.min(this.startMinNew[act], predState.t);
                    previousLayerToVisit.add(edgeList.node(e));
                }
            }
            Set<Integer> temp = currentLayerToVisit;
            currentLayerToVisit = previousLayerToVisit;
            previousLayerToVisit = temp;
        }
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            if (startMinNew[i] > startMin[i]) {
                changed = true;
                this.startMin[i] = startMinNew[i];
            }
        }
        return changed ? Outcome.CHANGE: Outcome.NO_CHANGE;
    }

    private void update(int[] startMin, int[] duration, int[] endMax, int n) {
        this.n = n;
        for (int i = 0; i < n; i++) {
            this.startMin[i] = startMin[i];
            this.startMinNew[i] = Integer.MAX_VALUE;
            this.startMax[i] = endMax[i] - duration[i];
            this.duration[i] = duration[i];
            this.endMin[i] = startMin[i] + duration[i];
            this.endMax[i] = endMax[i];
        }
    }

    private boolean contains(int set, int v) {
        return (set & (1 << v)) != 0;
    }
}