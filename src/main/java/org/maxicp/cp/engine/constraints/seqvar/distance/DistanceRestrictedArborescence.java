package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.constraints.seqvar.MinimumArborescence;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.modeling.algebra.sequence.SeqStatus;

public class DistanceRestrictedArborescence extends AbstractDistance {

    private MinimumArborescence minimumArborescence;
    int[][] preds;
    int[] numPreds;
    private EdgeIterator edgeIterator;
    private int[] members;

    public DistanceRestrictedArborescence(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        this.minimumArborescence = new MinimumArborescence(dist, seqVar.start());
        this.preds = new int[nNodes][nNodes];
        this.numPreds = new int[nNodes];
        this.members=new int[nNodes];
        edgeIterator = new SeqvarEdgeIterator(seqVar);

    }

    @Override
    public void updateLowerBound() {
        edgeIterator.update();
        for (int node = 0 ; node < nNodes ; node++) {
            numPreds[node] = edgeIterator.fillPred(node, preds[node]);
            for (int i=numPreds[node]; i<nNodes; i++){
                int pred=preds[node][i];
                if (pred==-1) // already marked as not possible predecessor
                    continue;
                int nMember=seqVar.fillNode(members, SeqStatus.MEMBER);
                boolean canBeInserted=false;
                for (int j=0;j<nMember;j++){
                    int member=members[j];
                    if(seqVar.hasEdge(member, node) && seqVar.hasEdge(member, pred)){
                        canBeInserted=true;
                        break;
                    }
                }
                if (!canBeInserted){
                    preds[node][i]=-1; // mark as not possible predecessor
                }
            }
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
