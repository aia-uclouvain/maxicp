package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;

public class DistanceMinInputAndOutput extends AbstractDistance {

    protected final int[] costMinRequiredPred; //  minimum cost of edges from required predecessors
    protected final int[] costMinRequiredSucc; //  minimum cost of edges from required successors
    protected final int[] costMin;
    private int lowerBound; // lower bound computed with this method
    private EdgeIterator edgeIterator;

    public DistanceMinInputAndOutput(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        costMinRequiredPred = new int[nNodes];
        costMinRequiredSucc = new int[nNodes];
        costMin = new int[nNodes];
        edgeIterator = new SeqvarEdgeIterator(seqVar);
    }

    @Override
    public void updateLowerBound() {
        edgeIterator.update();
        int totalCost = 0;


        int nRequired = seqVar.fillNode(nodes, REQUIRED);
        for (int i = 0; i < nRequired; i++) {
            int node = nodes[i];
            costMinRequiredPred[node] = Integer.MAX_VALUE;
            costMinRequiredSucc[node] = Integer.MAX_VALUE;
            int bestP = -1;
            int bestS = -1;
            int nextBestP = -1;
            int nextBestS = -1;

            //int nPred = seqVar.fillPred(node, inserts, REQUIRED); // gets all required predecessors
            int nPred = edgeIterator.fillPred(node, inserts, REQUIRED); // gets all required predecessors
            for (int j = 0; j < nPred; j++) {
                int pred = inserts[j];
                // update minimum cost for the predecessors
                if (bestP == -1 || dist[pred][node] < dist[bestP][node]) {
//                    costMinRequiredPred[node] = dist[pred][node];
                    nextBestP= bestP;
                    bestP = pred;
                }
            }
            //int nSucc = seqVar.fillSucc(node, inserts, REQUIRED); // gets all required successors
            int nSucc = edgeIterator.fillSucc(node, inserts, REQUIRED); // gets all required successors
            for (int j = 0; j < nSucc; j++) {
                int succ = inserts[j];
                // update minimum cost for the successors
                if (bestS == -1 || dist[node][succ] < dist[node][bestS]) {
//                    costMinRequiredSucc[node] = dist[node][succ];
                    nextBestS= bestS;
                    bestS = succ;
                }
            }

            int cost = 0;
            if (bestP != -1) {
                costMinRequiredPred[node] = dist[bestP][node];
                cost += costMinRequiredPred[node];
            }
            if (bestS != -1) {
                costMinRequiredSucc[node] = dist[node][bestS];
                cost += costMinRequiredSucc[node];
            }
            cost = cost / 2;
            costMin[node] = cost;
            totalCost += cost;
        }

        // remove the lower bound on the total distance
        totalDist.removeBelow(totalCost);
        lowerBound = totalCost;
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {
        if (lowerBound - costMinRequiredSucc[pred] / 2 - costMin[node] - costMinRequiredPred[succ] / 2 + detour > totalDist.max()) {
            seqVar.notBetween(pred, node, succ);
        }
    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {
        if (lowerBound - costMinRequiredSucc[pred] / 2 - costMinRequiredPred[succ] / 2 + detour > totalDist.max())
            seqVar.notBetween(pred, node, succ);
    }

}

