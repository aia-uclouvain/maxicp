package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;

public class DistanceMinInputAndOutputSum extends AbstractDistance {

    protected final int[] costMinRequiredPred; //  minimum cost of edges from required predecessors
    protected final int[] costMinRequiredSucc; //  minimum cost of edges from required successors
    protected final int[] costMin;
    private int lowerBound; // lower bound computed with this method

    public DistanceMinInputAndOutputSum(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        costMinRequiredPred = new int[nNodes];
        costMinRequiredSucc = new int[nNodes];
        costMin = new int[nNodes];
    }

    @Override
    public void updateLowerBound() {
        int totalCost = 0;

        int nRequired = seqVar.fillNode(nodes, REQUIRED);
        for (int i = 0; i < nRequired; i++) {
            int node = nodes[i];
            costMinRequiredPred[node] = Integer.MAX_VALUE;
            costMinRequiredSucc[node] = Integer.MAX_VALUE;
            int nPred = seqVar.fillPred(node, inserts, REQUIRED); // gets all required predecessors
            for (int j = 0; j < nPred; j++) {
                int pred = inserts[j];
                // update minimum cost for the predecessors
                if (dist[pred][node] < costMinRequiredPred[node]) {
                    costMinRequiredPred[node] = dist[pred][node];
                }
            }
            int nSucc = seqVar.fillSucc(node, inserts, REQUIRED); // gets all required successors
            for (int j = 0; j < nSucc; j++) {
                int succ = inserts[j];
                // update minimum cost for the successors
                if (dist[node][succ] < costMinRequiredSucc[node]) {
                    costMinRequiredSucc[node] = dist[node][succ];
                }
            }
            int cost = 0;
            if (costMinRequiredPred[node] < Integer.MAX_VALUE) {
                cost += costMinRequiredPred[node];
            }
            if  (costMinRequiredSucc[node] < Integer.MAX_VALUE) {
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
        //if (lowerBound - costMin[node] - costMin[succ] + detour > totalDist.max())
        //    seqVar.notBetween(pred, node, succ);
    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {
        //if (lowerBound - costMin[pred] + dist[pred][node] + dist[node][succ] > totalDist.max())
        //    seqVar.notBetween(pred, node, succ);
    }

}
