package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER;

public class DistanceForwardSlack extends AbstractDistance{

    private int[] minD;
    private int[] maxD;

    private int[] offsetToAddForward; // offsetToAdd[node], describe the minimum increment that must be done to the minD of the node
    private int[] offsetToAddBackward; // offsetToAdd[node], describe the minimum increment that must be done to the minD of the node

    private int[] earliestPred;
    private int[] latestSucc;

    public DistanceForwardSlack(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        minD = new int[seqVar.nNode()];
        maxD = new int[seqVar.nNode()];
        earliestPred = new int[seqVar.nNode()];
        latestSucc = new int[seqVar.nNode()];
        offsetToAddForward = new int[seqVar.nNode()];
        offsetToAddBackward = new int[seqVar.nNode()];
    }

    protected void updateMinAndMaxDist() {
        int nMember = seqVar.fillNode(nodes, MEMBER_ORDERED);
        // forward pass: set the distance from the start node
        currentDist = 0;
        for (int i = 0 ; i < nMember-1 ; i++) {
            int pred = nodes[i];
            int succ = nodes[i+1];
            currentDist += dist[pred][succ];
            minD[succ] = currentDist;
            offsetToAddForward[pred] = 0;
            offsetToAddBackward[pred] = 0;
        }
        totalDist.removeBelow(currentDist);
        offsetToAddForward[seqVar.end()] = 0;
        offsetToAddBackward[seqVar.end()] = 0;
        // backward pass: set the distance from the end node
        int succDist = totalDist.max();
        maxD[seqVar.end()] = succDist;
        for (int i = nMember-1 ; i > 0 ; i--) {
            int succ = nodes[i];
            int pred = nodes[i-1];
            succDist -= dist[pred][succ];
            maxD[pred] = succDist;
        }
    }

    protected void addMandatoryDetours() {
        int nInsertableRequired = seqVar.fillNode(nodes, INSERTABLE_REQUIRED);
        if (nInsertableRequired > 0) {
            // track the earliest pred, latest successor and min detour of all insertable nodes
            for (int i = 0 ; i < nInsertableRequired ; i++) {
                int node = nodes[i];
                int dBestPred = Integer.MAX_VALUE;
                int dBestSucc = 0;
                int minDetour = Integer.MAX_VALUE;
                int nInsert = seqVar.fillInsert(node, inserts);
                for (int j = 0 ; j < nInsert ; j++) {
                    int pred = inserts[j];
                    int succ = seqVar.memberAfter(pred);
                    int dPred = minD[pred];
                    int dSucc = minD[succ];
                    if (dPred < dBestPred) {
                        earliestPred[node] = pred;
                        dBestPred = dPred;
                        minD[node] = dPred + dist[pred][node];
                    }
                    if (dSucc > dBestSucc) {
                        latestSucc[node] = succ;
                        dBestSucc = dSucc;
                        maxD[node] = maxD[succ] - dist[node][succ];
                    }
                    int detour = dist[pred][node] + dist[succ][node] - dist[pred][succ];
                    minDetour = Math.min(minDetour, detour);
                }
                // a cost of minDetour needs to occur between earliestPred[node] and latestSucc[node]
                // this cost is stored in the array and processed after
                offsetToAddForward[latestSucc[node]] = Math.max(minDetour, offsetToAddForward[latestSucc[node]]);
                offsetToAddBackward[earliestPred[node]] = Math.max(minDetour, offsetToAddBackward[earliestPred[node]]);
            }
            // take those min detours values into account when adding the nodes
            int nMember = seqVar.fillNode(nodes, MEMBER_ORDERED);
            // add to distance from start
            int distanceIncrement = 0;
            for (int i = 0 ; i < nMember ; i++) {
                int node = nodes[i];
                distanceIncrement = Math.max(distanceIncrement, offsetToAddForward[node]);
                minD[node] += distanceIncrement;
            }
            totalDist.removeBelow(minD[seqVar.end()]);
            // add to distance from end
            distanceIncrement = 0;
            for (int i = nMember - 1 ; i >= 0 ; i--) {
                int node = nodes[i];
                distanceIncrement = Math.max(distanceIncrement, offsetToAddBackward[node]);
                maxD[node] -= distanceIncrement;
            }
            // TODO: could use tighter bounds if we know that a node must be put between [0..3] and the other one between [6..8]
            //  then the two nodes must be added
            //  we would need to tell if there's an overlap in the insertions of the nodes and add the two offsets if so
        }
    }

    int nMember;

    @Override
    public void propagate() {
        updateMinAndMaxDist();
        if (seqVar.isFixed()) {
            totalDist.fix(currentDist);
            setActive(false);
            return;
        } else {
            totalDist.removeBelow(currentDist);
        }
        addMandatoryDetours();
        int maxDetour = totalDist.max() - currentDist;
        nMember = seqVar.nNode(MEMBER);
        int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
        for (int i = 0 ; i < nInsertable ; i++) {
            int node = nodes[i];
            int nPreds = seqVar.fillInsert(node, inserts);
            for (int p = 0 ; p < nPreds ; p++) {
                int pred = inserts[p];
                filterEdge(pred, node, maxDetour);
            }
        }
    }

    @Override
    public void updateLowerBound() {

    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {
        int ea = minD[pred] + dist[pred][node];
        int la = maxD[succ] - dist[node][succ];
        if (ea > la) {
            seqVar.notBetween(pred, node, succ);
        }
    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {
        int ea = minD[pred] + dist[pred][node];
        int la = maxD[succ] - dist[node][succ];
        if (ea > la) {
            seqVar.notBetween(pred, node, succ);
        }
    }
}

/*
  bounds: 8 nodes
noBounds: 8 nodes
  bounds: 6 nodes
noBounds: 6 nodes
  bounds: 14 nodes
noBounds: 16 nodes
  bounds: 50 nodes
noBounds: 64 nodes
  bounds: 10534 nodes
noBounds: 18982 nodes
  bounds: 5546 nodes
noBounds: 9564 nodes
  bounds: 7024 nodes
noBounds: 10184 nodes
 */
