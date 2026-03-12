package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.Arrays;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;

public class DistanceMaxInputOrOutput extends AbstractDistance {

    protected final int[] costMinRequiredPred; //  minimum cost of edges from required predecessors
    protected final int[] costMinRequiredSucc; //  minimum cost of edges from required successors
    protected final int[] costMin;
    private int lowerBound; // lower bound computed with this method
    private final int[] lowerBoundWithOptional; // lower bound computed with this method when considering optional nodes as well
    private EdgeIterator edgeIterator;
    private boolean choosePred;

    public DistanceMaxInputOrOutput(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        costMinRequiredPred = new int[nNodes];
        costMinRequiredSucc = new int[nNodes];
        costMin = new int[nNodes];
        this.lowerBoundWithOptional = new int[nNodes];
        edgeIterator = new SeqvarEdgeIterator(seqVar);
    }

    @Override
    public void updateLowerBound() {
        Arrays.fill(lowerBoundWithOptional, Integer.MAX_VALUE);
        edgeIterator.update();
        int totalCost = 0;
        int costPred = 0;
        int costSucc = 0;

        int nRequired = seqVar.fillNode(nodes, REQUIRED);
        for (int i = 0; i < nRequired; i++) {
            int node = nodes[i];

            updateMinPredMinSucc(node);

            if (costMinRequiredPred[node] < Integer.MAX_VALUE) {
                costPred += costMinRequiredPred[node];
            }
            if (costMinRequiredSucc[node] < Integer.MAX_VALUE) {
                costSucc += costMinRequiredSucc[node];
            }
        }

        if (costPred > costSucc) {
            totalCost = costPred;
            choosePred = true;
        } else {
            totalCost = costSucc;
            choosePred = false;
        }

        // remove the lower bound on the total distance
        totalDist.removeBelow(totalCost);
        lowerBound = totalCost;
    }

    private void updateMinPredMinSucc(int node) {
        costMinRequiredPred[node] = Integer.MAX_VALUE;
        costMinRequiredSucc[node] = Integer.MAX_VALUE;
        //int nPred = seqVar.fillPred(node, inserts, REQUIRED); // gets all required predecessors
        int nPred = edgeIterator.fillPred(node, inserts, REQUIRED); // gets all required predecessors
        for (int j = 0; j < nPred; j++) {
            int pred = inserts[j];
            // update minimum cost for the predecessors
            if (dist[pred][node] < costMinRequiredPred[node]) {
                costMinRequiredPred[node] = dist[pred][node];
            }
        }
        //int nSucc = seqVar.fillSucc(node, inserts, REQUIRED); // gets all required successors
        int nSucc = edgeIterator.fillSucc(node, inserts, REQUIRED); // gets all required successors
        for (int j = 0; j < nSucc; j++) {
            int succ = inserts[j];
            // update minimum cost for the successors
            if (dist[node][succ] < costMinRequiredSucc[node]) {
                costMinRequiredSucc[node] = dist[node][succ];
            }
        }
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {
        if (this.choosePred) {
            if (lowerBound - costMinRequiredPred[node] - costMinRequiredPred[succ] + detour > totalDist.max()) {
                seqVar.notBetween(pred, node, succ);
            }
        } else {
            if (lowerBound - costMinRequiredSucc[pred] - costMinRequiredSucc[node] + detour > totalDist.max()) {
                seqVar.notBetween(pred, node, succ);
            }
        }
    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {

        if (lowerBoundWithOptional[node] == Integer.MAX_VALUE) {
            updateMinPredMinSucc(node);
            lowerBoundWithOptional[node] = lowerBound;
            int nRequired = seqVar.fillNode(nodes, REQUIRED);

            for (int i = 0; i < nRequired; i++) {
                int n = nodes[i];

                if (this.choosePred && seqVar.hasEdge(node, n) && dist[node][n] < costMinRequiredPred[n]) {
                    lowerBoundWithOptional[node] -= (costMinRequiredPred[n] - dist[node][n]);
                }

                if (!this.choosePred && seqVar.hasEdge(n, node) && dist[n][node] < costMinRequiredSucc[n]) {
                    lowerBoundWithOptional[node] -= (costMinRequiredSucc[n] - dist[n][node]);
                }

            }
        }

        if (this.choosePred) {
            if (lowerBoundWithOptional[node] - costMinRequiredPred[succ] + detour > totalDist.max()) {
                seqVar.notBetween(pred, node, succ);
            }
        } else {
            if (lowerBoundWithOptional[node] - costMinRequiredSucc[pred] + detour > totalDist.max()) {
                seqVar.notBetween(pred, node, succ);
            }
        }
    }

}