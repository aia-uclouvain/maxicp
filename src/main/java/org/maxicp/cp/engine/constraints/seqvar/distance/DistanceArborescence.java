package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.constraints.seqvar.MinimumArborescence;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;

public class DistanceArborescence extends AbstractDistance {

    private MinimumArborescence minimumArborescence;
    int[][] preds;
    int[] numPreds;
    private EdgeIterator edgeIterator;

    public DistanceArborescence(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        this.minimumArborescence = new MinimumArborescence(dist, seqVar.start());
        this.preds = new int[nNodes][nNodes];
        this.numPreds = new int[nNodes];
        edgeIterator = new SeqvarEdgeIterator(seqVar);

    }

    @Override
    public void updateLowerBound() {
        edgeIterator.update();
        int nNodesRequired = seqVar.fillNode(nodes, REQUIRED);
        for (int i = 0; i < nNodesRequired; i++) {
            int node = nodes[i];
            numPreds[node] = edgeIterator.fillPred(node, preds[node], REQUIRED);
        }
        minimumArborescence.findMinimumArborescence(preds, numPreds);

        totalDist.removeBelow(minimumArborescence.getCostMinimumArborescence());
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {

    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {

    }
}
