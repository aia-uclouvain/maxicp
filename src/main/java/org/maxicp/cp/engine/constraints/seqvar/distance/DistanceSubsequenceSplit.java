package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.Arrays;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public class DistanceSubsequenceSplit extends AbstractDistance {

    private int[] distFromStart;
    private int[] distFromEnd;

    private int[] offsetToAddForward; // offsetToAdd[node], describe the minimum increment that must be done to the minD of the node
    private int[] offsetToAddBackward; // offsetToAdd[node], describe the minimum increment that must be done to the minD of the node

    private int[] earliestPred;
    private int[] latestSucc;

    public DistanceSubsequenceSplit(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        distFromStart = new int[seqVar.nNode()];
        distFromEnd = new int[seqVar.nNode()];
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
            distFromStart[succ] = currentDist;
            offsetToAddForward[pred] = 0;
            offsetToAddBackward[pred] = 0;
        }
        totalDist.removeBelow(currentDist);
        offsetToAddForward[seqVar.end()] = 0;
        offsetToAddBackward[seqVar.end()] = 0;
        // backward pass: set the distance from the end node
        int succDist = 0;
        for (int i = nMember-1 ; i > 0 ; i--) {
            int succ = nodes[i];
            int pred = nodes[i-1];
            succDist += dist[pred][succ];
            distFromEnd[pred] = succDist;
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
                    int dPred = distFromStart[pred];
                    int dSucc = distFromStart[succ];
                    if (dPred < dBestPred) {
                        earliestPred[node] = pred;
                        dBestPred = dPred;
                        distFromStart[node] = dPred + dist[pred][node];
                    }
                    if (dSucc > dBestSucc) {
                        latestSucc[node] = succ;
                        dBestSucc = dSucc;
                        distFromEnd[node] = distFromEnd[succ] + dist[node][succ];
                    }
                    int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
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
                offsetToAddForward[node] = distanceIncrement;
            }


            totalDist.removeBelow(distFromStart[seqVar.end()] + offsetToAddForward[seqVar.end()]);
            // add to distance from end
            distanceIncrement = 0;
            for (int i = nMember - 1 ; i >= 0 ; i--) {
                int node = nodes[i];
                distanceIncrement = Math.max(distanceIncrement, offsetToAddBackward[node]);
                offsetToAddBackward[node] = distanceIncrement;
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

    private int costBefore(int node) {
        return distFromStart[node] + offsetToAddForward[node];
    }

    private int costAfter(int node) {
        return distFromEnd[node] + offsetToAddBackward[node];
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {
        int costBefore = costBefore(pred);
        int costAfter = costAfter(succ);
        int distEstimate = costBefore + dist[pred][node] + dist[node][succ] + costAfter;
        assert (seqVar.nNode(MEMBER) != nMember) || (distEstimate >= distFromStart[seqVar.end()]);
        if (distEstimate > totalDist.max()) {
            seqVar.notBetween(pred, node, succ);
        }
    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {
        int costBefore = costBefore(pred);
        int costAfter = costAfter(succ);
        int distEstimate = costBefore + dist[pred][node] + dist[node][succ] + costAfter;
        assert (seqVar.nNode(MEMBER) != nMember) || (distEstimate >= distFromStart[seqVar.end()]);
        if (distEstimate > totalDist.max()) {
            seqVar.notBetween(pred, node, succ);
        }
    }
}
