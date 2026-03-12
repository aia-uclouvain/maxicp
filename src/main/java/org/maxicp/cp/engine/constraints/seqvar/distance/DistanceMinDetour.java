package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public class DistanceMinDetour extends AbstractDistance{

    private int lowerBound;
    private final int[] preds;
    private final int[] succs;
    private final int[] minDetour;

    private EdgeIterator edgeIterator;

    public DistanceMinDetour(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        this.preds = new int[nNodes];
        this.succs = new int[nNodes];
        this.minDetour = new int[nNodes];
        edgeIterator = new SeqvarEdgeIterator(seqVar);
    }

    @Override
    public void updateLowerBound() {
        edgeIterator.update();
        int totalMinDetour = 0;

        int nInsertable = seqVar.fillNode(nodes, INSERTABLE_REQUIRED);
        for (int n = 0; n < nInsertable; n++) {
            int node = nodes[n];
            minDetour[node] = Integer.MAX_VALUE;
            int nInsert = seqVar.fillInsert(node, inserts);
            // update with the detours currently in the sequence
            for (int i = 0 ; i < nInsert ; i++) //(member, insertable, member)
                updateMinDetour(inserts[i], node, seqVar.memberAfter(inserts[i]));
            for (int j = 0 ; j < nInsertable ; j++) {
                if (j == n)
                    continue;
                // detours on the edges within the sequence
                for (int i = 0 ; i < nInsert ; i++) { //(member, insertable, insertable) ET (insertable, insertable, member)
                    int pred = inserts[i];
                    int otherInsertable = nodes[j];
                    if (seqVar.hasEdge(pred, otherInsertable)) { // a chain pred -> otherInsertable -> next(pred) can be formed
                        updateMinDetour(pred, node, otherInsertable); // insert on pred -> otherInsertable ?
                        updateMinDetour(otherInsertable, node, seqVar.memberAfter(pred)); // insert on otherInsertable -> next(pred)?
                    }
                }
                // detours with insertable nodes
                for (int k = j+1 ; k < nInsertable ; k++) {  //(insertable, insertable, insertable)
                    if (k == n)
                        continue;
                    updateMinDetour(nodes[j], node, nodes[k]);
                    updateMinDetour(nodes[k], node, nodes[j]);
                }
            }
            if (minDetour[node] < Integer.MAX_VALUE) {
                totalMinDetour += minDetour[node];
            }
        }

        // remove the lower bound on the total distance
        lowerBound = currentDist + totalMinDetour;
        totalDist.removeBelow(lowerBound);
    }

    public void updateMinDetour(int pred, int node, int succ) {
        int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
        minDetour[node] = Math.min(minDetour[node], detour);
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
