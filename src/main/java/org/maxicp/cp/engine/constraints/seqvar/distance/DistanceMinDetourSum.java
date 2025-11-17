package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.Arrays;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public class DistanceMinDetourSum extends AbstractDistance{

    private int lowerBound;
    private final int[][] preds;
    private final int[] numPreds;
    private final int[][] succs;
    private final int[] numSuccs;
    private final int[] minDetour;

    private EdgeIterator edgeIterator;

    public DistanceMinDetourSum(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        this.preds = new int[nNodes][nNodes];
        this.numPreds = new int[nNodes];
        this.succs = new int[nNodes][nNodes];
        this.numSuccs = new int[nNodes];
        this.minDetour = new int[nNodes];
        edgeIterator = new SeqvarEdgeIterator(seqVar);
    }

    @Override
    public void updateLowerBound() {
        edgeIterator.update();
        for (int i = 0; i < nNodes; i++) { // from variables to values
            numSuccs[i] = seqVar.fillSucc(i, succs[i]);
            numPreds[i] = seqVar.fillPred(i, preds[i]);
        }
        Arrays.fill(minDetour, Integer.MAX_VALUE);

        int totalMinDetour = 0;

        int nInsertable = seqVar.fillNode(nodes, INSERTABLE_REQUIRED);
        for (int n = 0; n < nInsertable; n++) {
            int node = nodes[n];
            for (int p = 0; p < numPreds[node]; p++) {
                int pred = preds[node][p];
                if (!seqVar.isNode(pred, REQUIRED)) {
                    continue;
                }
                for (int s = 0; s < numSuccs[node]; s++) {
                    int succ = succs[node][s];
                    if (pred == succ || !seqVar.isNode(succ, REQUIRED)) {
                        continue; // skip if pred and succ are the same
                    }
                    if (edgeIterator.hasEdge(pred, succ) && edgeIterator.hasEdge(pred, node) && edgeIterator.hasEdge(node, succ)) {
                        int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
                        if (detour < minDetour[node]) {
                            minDetour[node] = detour;
                        }
                    }
                }
            }
            if (minDetour[node] < Integer.MAX_VALUE) {
                totalMinDetour += minDetour[node];
            }
        }

        // remove the lower bound on the total distance
        lowerBound = currentDist + totalMinDetour;
        totalDist.removeBelow(currentDist + totalMinDetour);
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {
        if (lowerBound - minDetour[node] + detour > totalDist.max()) {
            seqVar.notBetween(pred, node, succ);
        }
    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {
        if (lowerBound + detour > totalDist.max()) {
            seqVar.notBetween(pred, node, succ);
        }
    }
}
