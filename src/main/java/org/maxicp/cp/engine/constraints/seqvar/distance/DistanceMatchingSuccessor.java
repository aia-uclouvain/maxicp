package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.constraints.MinCostMaxFlow;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.Arrays;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;

public class DistanceMatchingSuccessor extends AbstractDistance{

    private final int[][] succs;
    private final int[] numSuccs;
    private MinCostMaxFlow minCostMaxFlow;
    private int numNodesInMatching;
    private int[][] capMaxNetworkFlow;
    private int[][] costNetworkFlow;

    public DistanceMatchingSuccessor(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        this.succs = new int[nNodes][nNodes];
        this.numSuccs = new int[nNodes];
        this.numNodesInMatching = nNodes + nNodes + 2; // numNodes + numNodes + source + dest
        this.minCostMaxFlow = new MinCostMaxFlow(numNodesInMatching);
        this.capMaxNetworkFlow = new int[numNodesInMatching][numNodesInMatching];
        this.costNetworkFlow = new int[numNodesInMatching][numNodesInMatching];
    }

    @Override
    public void updateLowerBound() {
        for (int i = 0; i < numNodesInMatching; i++) {
            Arrays.fill(capMaxNetworkFlow[i], 0);
            Arrays.fill(costNetworkFlow[i], 0);
        }
        for (int i = 0; i < nNodes; i++) { // from variables to values
            numSuccs[i] = seqVar.fillSucc(i, succs[i]);
        }

        for (int i = 0; i < nNodes; i++) {
            capMaxNetworkFlow[0][i + 1] = 1; // source to first layer
        }
        for (int i = 0; i < nNodes; i++) {
            capMaxNetworkFlow[nNodes + i + 1][numNodesInMatching - 1] = 1; // second layer to sink
        }
        int succ;
        for (int i = 0; i < nNodes; i++) {
            for (int j = 0; j < numSuccs[i]; j++) {
                succ = succs[i][j];
                capMaxNetworkFlow[i + 1][nNodes + succ + 1] = 1; // first layer to second layer
                costNetworkFlow[i + 1][nNodes + succ + 1] = dist[i][succ];
            }
            if (!seqVar.isNode(i, REQUIRED)) {
                capMaxNetworkFlow[i + 1][nNodes + i + 1] = 1;
            }
        }

        minCostMaxFlow.run(totalDist.max(), 0, numNodesInMatching - 1, capMaxNetworkFlow, costNetworkFlow);

        totalDist.removeBelow(minCostMaxFlow.getTotalCost());
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {

    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {

    }
}
