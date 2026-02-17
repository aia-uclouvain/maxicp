package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;

public class DistanceRestrictedMaxInputOrOutput extends DistanceMaxInputOrOutput {

    protected final int[] costMinRequiredPred; //  minimum cost of edges from required predecessors
    protected final int[] costMinRequiredSucc; //  minimum cost of edges from required successors
    protected final int[] costMin;
    private int lowerBound; // lower bound computed with this method
    private EdgeIterator edgeIterator;
    private boolean choosePred;
    private int[] members;


    public DistanceRestrictedMaxInputOrOutput(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        costMinRequiredPred = new int[nNodes];
        costMinRequiredSucc = new int[nNodes];
        costMin = new int[nNodes];
        members = new int[nNodes];
        edgeIterator = new SeqvarEdgeIterator(seqVar);
    }

    @Override
    public void updateLowerBound() {
        edgeIterator.update();
        int totalCost = 0;
        int costPred = 0;
        int costSucc = 0;

        int nRequired = seqVar.fillNode(nodes, REQUIRED);
        for (int i = 0; i < nRequired; i++) {
            int node = nodes[i];
            costMinRequiredPred[node] = Integer.MAX_VALUE;
            costMinRequiredSucc[node] = Integer.MAX_VALUE;
            //int nPred = seqVar.fillPred(node, inserts, REQUIRED); // gets all required predecessors
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
            //int nSucc = seqVar.fillSucc(node, inserts, REQUIRED); // gets all required successors
            int nSucc = edgeIterator.fillSucc(node, inserts, REQUIRED); // gets all required successors
            for (int j = 0; j < nSucc; j++) {
                int succ = inserts[j];
                if (!seqVar.isNode(node,MEMBER) && !seqVar.isNode(succ, MEMBER)){
                    int nMember=seqVar.fillNode(members, MEMBER);
                    boolean canBeInserted=false;
                    for (int k=0;k<nMember;k++){
                        int member=members[k];
                        if(seqVar.hasEdge(member, succ) && seqVar.hasEdge(member, node)){
                            canBeInserted=true;
                            break;
                        }
                    }
                    if (canBeInserted && dist[node][succ] < costMinRequiredSucc[node]) {
                        costMinRequiredSucc[node] = dist[node][succ];
                    }
                }
                else if (dist[node][succ] < costMinRequiredSucc[node]) {
                    costMinRequiredSucc[node] = dist[node][succ];
                }
            }

            if (costMinRequiredPred[node] < Integer.MAX_VALUE) {
                costPred += costMinRequiredPred[node];
            }
            if  (costMinRequiredSucc[node] < Integer.MAX_VALUE) {
                costSucc += costMinRequiredSucc[node];
            }
//            cost = cost / 2;
//            costMin[node] = cost;
//            totalCost += cost;
        }

        if(costPred>costSucc){
            totalCost=costPred;
            choosePred =true;
        }
        else {
            totalCost=costSucc;
            choosePred =false;
        }

        // remove the lower bound on the total distance
        totalDist.removeBelow(totalCost);
        lowerBound = totalCost;
    }

}