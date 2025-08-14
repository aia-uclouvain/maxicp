/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.state.StateManager;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Stack;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Depth-First Search Branch and Bound implementation
 */
public class DFSearch extends RunnableSearchMethod {

    private int currNodeId = -1;

    public DFSearch(StateManager sm, Supplier<Runnable[]> branching) {
        super(sm, branching);
    }
    public DFSearch(ModelProxy modelProxy, Supplier<Runnable[]> branching) { super(modelProxy.getConcreteModel().getStateManager(), branching); }

    // solution to DFS with explicit stack
    private void expandNode(Stack<Runnable> alternatives, SearchStatistics statistics, Runnable onNodeVisit, DFSListener listener, int parentId) {
        Runnable[] alts = branching.get();
        if (alts.length == 0) {
            statistics.incrSolutions();
            notifySolution();
        } else {
            for (int i = alts.length - 1; i >= 0; i--) {
                int nodeId = currNodeId++;
                Runnable a = alts[i];
                alternatives.push(() -> sm.restoreState());
                alternatives.push(() -> {
                    listener.branch(nodeId, parentId);
                    statistics.incrNodes();
                    onNodeVisit.run();
                    a.run();
                    expandNode(alternatives, statistics, onNodeVisit, listener, nodeId);
                });
                alternatives.push(() -> sm.saveState());
            }
        }
    }

    @Override
    protected void startSolve(SearchStatistics statistics, Predicate<SearchStatistics> limit, Runnable onNodeVisit, DFSListener listener) {
        currNodeId = -1;
        Stack<Runnable> alternatives = new Stack<Runnable>();
        expandNode(alternatives, statistics, onNodeVisit, listener, currNodeId);
        while (!alternatives.isEmpty()) {
            if (limit.test(statistics)) throw new StopSearchException();
            try {
                alternatives.pop().run();
            } catch (InconsistencyException e) {
                statistics.incrFailures();
                notifyFailure();
            }
        }
    }
}
