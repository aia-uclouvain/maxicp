package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.constraints.seqvar.MinimumArborescence;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

public class DistanceArborescence extends AbstractDistance {

    private MinimumArborescence minimumArborescence;
    int[][] preds;
    int[] numPreds;

    public DistanceArborescence(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        this.minimumArborescence = new MinimumArborescence(dist, seqVar.start());
        this.preds = new int[nNodes][nNodes];
        this.numPreds = new int[nNodes];
    }

    @Override
    public void updateLowerBound() {
        for (int node = 0 ; node < nNodes ; node++) {
            numPreds[node] = seqVar.fillPred(node, preds[node]);
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
