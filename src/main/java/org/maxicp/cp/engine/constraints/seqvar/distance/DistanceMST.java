package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

public class DistanceMST extends AbstractDistance {

    private MST minimumSpanningTree;

    public DistanceMST(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        int[][] costForMST = new int[nNodes][nNodes];
        for (int i = 0; i < nNodes; i++) {
            for (int j = i; j < nNodes; j++) {
                costForMST[i][j] = Math.min(dist[i][j], dist[j][i]); // ensure symmetry
                costForMST[j][i] = costForMST[i][j]; // ensure symmetry
            }
        }
        this.minimumSpanningTree = new MST(seqVar, costForMST);
    }

    @Override
    public void updateLowerBound() {
        minimumSpanningTree.compute();
        totalDist.removeBelow(minimumSpanningTree.cost());
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {

    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {

    }
}
