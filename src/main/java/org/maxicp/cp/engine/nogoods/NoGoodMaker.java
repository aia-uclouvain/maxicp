package org.maxicp.cp.engine.nogoods;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Stack;

import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSListener;
import org.maxicp.search.DFSearch;

public class NoGoodMaker {
    private class NGListener implements DFSListener {
        protected boolean currentlyExecutingBranchingAction = false;
        protected ArrayList<CPConstraint> currentBranchingConstraints = null;

        @Override
        public void fail(int id, int pId) {
            assert currentlyExecutingBranchingAction;
            currentlyExecutingBranchingAction = false;
            nodeDone(id, pId, currentBranchingConstraints);
        }

        @Override
        public void branch(int id, int pId) {
            assert currentlyExecutingBranchingAction;
            currentlyExecutingBranchingAction = false;
            nodeDone(id, pId, currentBranchingConstraints);
        }

        @Override
        public void branchingAction(Runnable action) {
            currentlyExecutingBranchingAction = true;
            currentBranchingConstraints = new ArrayList<>();
        }

        public void beforeConstraintPosted(CPConstraint c) {
            if (!currentlyExecutingBranchingAction)
                return; // no need to store anything
            if (solver.isFixPointRunning())
                return; // this is a constraint added as a consequence of the fix-point, we only want to
                        // store constraints that are added as a consequence of branching decisions
            currentBranchingConstraints.add(c);
        }
    }

    /**
     * Stores the constraints added at each node of the search tree.
     * Seen during construction, branchingConstraints has the property that
     * 1) it is non-empty
     * 2) at the entries except the last one have been refuted (that is, the search
     * has backtracked from them)
     * 3) the last entry is ongoing (that is, the search is currently exploring the
     * subtree below this node)
     */
    public record NodeStatus(int nodeId, ArrayList<ArrayList<CPConstraint>> branchingConstraints) {
    }

    protected CPSolver solver;
    protected DFSearch search;
    protected NGListener ngListener;
    protected ArrayDeque<NodeStatus> nodeStatuses = new ArrayDeque<>();

    public NoGoodMaker(CPSolver solver, DFSearch search) {
        this.solver = solver;
        this.search = search;
        ngListener = new NGListener();
        solver.onBeforeConstraintPosted(ngListener::beforeConstraintPosted);
        search.setDFSListener(ngListener);
    }

    protected void nodeDone(int id, int pId, ArrayList<CPConstraint> branchingConstraints) {
        while (nodeStatuses.size() != 0
                && (nodeStatuses.getLast().nodeId != pId || nodeStatuses.getLast().nodeId != id)) {
            nodeStatuses.removeLast();
        }

        // Add a new node if needed (if the stack is empty or if the last node is the
        // parent of the current node)
        if (nodeStatuses.size() == 0 || nodeStatuses.getLast().nodeId == pId)
            nodeStatuses.addLast(new NodeStatus(id, new ArrayList<>()));

        nodeStatuses.getLast().branchingConstraints.add(branchingConstraints);
    }

    public ArrayDeque<NodeStatus> getNoGood() {
        return nodeStatuses;
    }
}
