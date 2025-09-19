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

    private static final DFSListener EMPTY_LISTENER = new DFSListener(){};
    private DFSListener dfsListener = EMPTY_LISTENER;

    public void setDFSListener(DFSListener listener) {
        this.dfsListener = listener;
    }

    private void notifySolution(int nodeId, int parentId) {
        dfsListener.solution(nodeId, parentId);
    }

    private void notifyFailure(int nodeId, int parentId) {
        dfsListener.fail(nodeId, parentId);
    }

    private void notifyBranch(int nodeId, int parentId) {
        dfsListener.branch(nodeId, parentId);
    }

    private void notifyBranchAction(Runnable action) {
        dfsListener.branchingAction(action);
    }

    private void notifySaveState() {
        dfsListener.saveState(sm);
    }

    private void notifyRestoreState() {
        dfsListener.restoreState(sm);
    }

    private int currNodeId = -1;

    public DFSearch(StateManager sm, Supplier<Runnable[]> branching) {
        super(sm, branching);
    }
    public DFSearch(ModelProxy modelProxy, Supplier<Runnable[]> branching) { super(modelProxy.getConcreteModel().getStateManager(), branching); }

    // solution to DFS with explicit stack
    private void expandNode(Stack<Runnable> alternatives, SearchStatistics statistics, Runnable onNodeVisit, int parentId) {
        Runnable[] alts = branching.get();
        if (alts.length == 0) {
            statistics.incrSolutions();
            notifySolution(currNodeId++, parentId);
            notifySolution();
        } else {
            for (int i = alts.length - 1; i >= 0; i--) {
                int nodeId = currNodeId++;
                Runnable a = alts[i];
                alternatives.push(() -> {
                    notifyRestoreState();
                    sm.restoreState();
                });
                alternatives.push(() -> {
                    statistics.incrNodes();
                    onNodeVisit.run();
                    try {
                        notifyBranchAction(a);
                        a.run();
                        notifyBranch(nodeId, parentId);
                        expandNode(alternatives, statistics, onNodeVisit, nodeId);
                    } catch (InconsistencyException e) {
                        notifyFailure(nodeId, parentId);
                        throw e;
                    }
                });
                alternatives.push(() -> {
                    notifySaveState();
                    sm.saveState();
                });
            }
        }
    }

    @Override
    protected void startSolve(SearchStatistics statistics, Predicate<SearchStatistics> limit, Runnable onNodeVisit) {
        currNodeId = -1;
        Stack<Runnable> alternatives = new Stack<Runnable>();
        expandNode(alternatives, statistics, onNodeVisit, currNodeId);
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


    public SearchStatistics solve(DFSListener dfsListener) {
        setDFSListener(dfsListener);
        SearchStatistics stats = super.solve();
        setDFSListener(EMPTY_LISTENER);
        return stats;
    }

    public SearchStatistics optimize(Objective obj, DFSListener dfsListener) {
        setDFSListener(dfsListener);
        SearchStatistics stats = super.optimize(obj);
        setDFSListener(EMPTY_LISTENER);
        return stats;
    }
}
