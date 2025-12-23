package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.state.datastructures.StateTriPartition;

import java.util.Arrays;
import java.util.Comparator;

public class NoOverlapPrecedenceGraph extends AbstractCPConstraint {

    final CPIntervalVar[] vars;
    private StateTriPartition[] predecessors, successors;
    private Integer[] iterator1;
    private Integer[] iterator2;

    public NoOverlapPrecedenceGraph(CPIntervalVar... vars) {
        super(vars[0].getSolver());
        iterator1 = new Integer[vars.length];
        iterator2 = new Integer[vars.length];
        predecessors = new StateTriPartition[vars.length];
        successors = new StateTriPartition[vars.length];
        for (int i = 0; i < vars.length; i++) {
            predecessors[i] = new StateTriPartition(getSolver().getStateManager(), vars.length);
            successors[i] = new StateTriPartition(getSolver().getStateManager(), vars.length);
        }
        this.vars = vars;
    }

    @Override
    public void post() {
        for (CPIntervalVar var : vars) {
            var.propagateOnChange(this);
        }
    }

    public void addPrecedence(int i, int j) {
        // i -> j
        if (predecessors[j].isIncluded(i)) {
            return; // already known
        }
        predecessors[j].include(i);
        successors[i].include(j);
        // transitive closure
        int nPred = predecessors[i].fillIncluded(iterator1);
        int nSucc = successors[j].fillIncluded(iterator2);

        for (int k = 0; k < nPred; k++) {
            int predi = iterator1[k];
            // all predecessors of i are predecessors of j
            predecessors[j].include(predi);
            for (int l = 0; l < nSucc; l++) {
                int succj = iterator2[l];
                // all predecessors of i are predecessors of all successors of j
                predecessors[succj].include(predi);
                // all successors of j are successors of i
                successors[i].include(succj);
                // all successors of j are successors of all predecessors of i
                successors[predi].include(succj);
            }
        }
        propagate();
    }

    public int fillPredecessors(int varIdx, int[] out) {
        return predecessors[varIdx].fillIncluded(out);
    }

    public int fillSuccessors(int varIdx, int[] out) {
        return successors[varIdx].fillIncluded(out);
    }

    @Override
    public void propagate() {
        // left to right
        for (int i = 0; i < vars.length; i++) {
            int nPred = predecessors[i].fillIncluded(iterator1);
            // sort by est
            Arrays.sort(iterator1, 0, nPred, Comparator.comparingInt(j -> vars[j].startMin()));
            int t = Integer.MIN_VALUE;
            for (int j = 0; j < nPred; j++) {
                int predIdx = iterator1[j];
                t = Math.max(t, vars[predIdx].startMin()) + vars[predIdx].lengthMin();
            }
            vars[i].setStartMin(t);
        }
        // right to left
        for (int i = vars.length - 1; i >= 0; i--) {
            int nSucc = successors[i].fillIncluded(iterator1);
            // sort by lct
            Arrays.sort(iterator1, 0, nSucc, Comparator.comparingInt(j -> -vars[j].endMax()));
            int t = Integer.MAX_VALUE;
            for (int j = 0; j < nSucc; j++) {
                int succIdx = iterator1[j];
                t = Math.min(t, vars[succIdx].endMax()) - vars[succIdx].lengthMin();
            }
            vars[i].setEndMax(t);
        }
        super.propagate();
    }
}

