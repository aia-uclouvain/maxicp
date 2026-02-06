package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;

public class DistanceMinRestrictedInput extends AbstractDistance {

    protected final int[] costMinRequiredPred; //  minimum cost of edges from required predecessors
    private int lowerBound; // lower bound computed with this method
    private EdgeIterator edgeIterator;
    private int[] members;

    public DistanceMinRestrictedInput(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        costMinRequiredPred = new int[nNodes];
        edgeIterator = new SeqvarEdgeIterator(seqVar);
        members = new int[nNodes];
    }

    @Override
    public void updateLowerBound() {
        edgeIterator.update();
        int totalMinPred = 0;

        int nRequired = seqVar.fillNode(nodes, REQUIRED);
        for (int i = 0; i < nRequired; i++) {
            int node = nodes[i];
            costMinRequiredPred[node] = Integer.MAX_VALUE;
            int nPred = edgeIterator.fillPred(node, inserts, REQUIRED); // gets all required predecessors
            for (int j = 0; j < nPred; j++) {
                int pred = inserts[j];
                if (!seqVar.isNode(node,MEMBER) && !seqVar.isNode(pred, MEMBER)){
                    int nMember=seqVar.fillNode(members, MEMBER);
                    boolean canBeInserted=false;
                    for (int k=0;k<nMember;k++){
                        int member=members[k];
                        if(seqVar.hasEdge(member, pred) && seqVar.hasEdge(member, node)){
                            canBeInserted=true;
                            break;
                        }
                    }
                    if (canBeInserted && dist[pred][node] < costMinRequiredPred[node]) {
                        costMinRequiredPred[node] = dist[pred][node];
                    }
                }
                else if (dist[pred][node] < costMinRequiredPred[node]) {
                    costMinRequiredPred[node] = dist[pred][node];
                }
            }
            if (costMinRequiredPred[node] < Integer.MAX_VALUE)
                totalMinPred += costMinRequiredPred[node];
        }

        // remove the lower bound on the total distance
        totalDist.removeBelow(totalMinPred);
        lowerBound = totalMinPred;
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {
        if (lowerBound - costMinRequiredPred[node] - costMinRequiredPred[succ] + detour > totalDist.max())
            seqVar.notBetween(pred, node, succ);
    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {
        if (lowerBound - costMinRequiredPred[succ] + detour > totalDist.max())
            seqVar.notBetween(pred, node, succ);
    }
}
