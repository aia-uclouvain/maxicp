package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public class DistanceMinDetour extends AbstractDistance{

    private int lowerBound;
    // cost of min detour computed on the required nodes only
    private final int[] minDetourOnRequired;
    // cost of min detour computed on potential other nodes
    private final int[] minDetour;
    private int lowerBoundForOptional;

    // buffer of required nodes (including member nodes)
    private final int[] required;
    private int nRequired;

    // buffer of insertable and required nodes
    private final int[] requiredInsertable;
    private int nRequiredInsertable;

    private EdgeIterator edgeIterator;

    public DistanceMinDetour(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        this.minDetourOnRequired = new int[nNodes];
        this.minDetour = new int[nNodes];
        this.requiredInsertable = new int[nNodes];
        this.required = new int[nNodes];
        edgeIterator = new SeqvarEdgeIterator(seqVar);
    }

    @Override
    public void propagate() {
        // step 1: reach fix point on the original filtering
        propagateOriginal();
        // step 2: apply min detour filtering
        propagateMinDetour();
    }

    /**
     * Original filtering, embedded in its own fixpoint
     */
    private void propagateOriginal() {
        boolean modified = true;
        while (modified) {
            modified = false;
            int nMember = seqVar.fillNode(nodes, MEMBER_ORDERED);
            currentDist = 0;
            for (int i = 0 ; i < nMember-1 ; ++i) {
                currentDist += dist[nodes[i]][nodes[i+1]];
            }
            if (seqVar.isFixed()) {
                totalDist.fix(currentDist);
                setActive(false);
                return;
            } else {
                totalDist.removeBelow(currentDist);
            }
            int maxDetour = totalDist.max() - currentDist;
            int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
            for (int i = 0 ; i < nInsertable ; i++) {
                int node = nodes[i];
                int nInsert = seqVar.fillInsert(node, inserts);
                for (int p = 0 ; p < nInsert ; p++) {
                    int pred = inserts[p];
                    int succ = seqVar.memberAfter(pred);
                    int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
                    if (detour > maxDetour) { // detour is too long
                        seqVar.notBetween(pred, node, succ); // first obvious filtering
                    }
                }
                int nInsertAfter = seqVar.nInsert(node);
                modified = modified || (nInsertAfter != nInsert);
            }
        }
    }

    private void propagateMinDetour() {
        updateLowerBound();
        nRequired = seqVar.fillNode(required, REQUIRED);
        int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
        for (int i = 0 ; i < nInsertable ; i++) {
            int node = nodes[i];
            int nInsert = seqVar.fillInsert(node, inserts);
            if (seqVar.isNode(node, REQUIRED)) {
                for (int j = 0 ; j < nInsert ; j++) {
                    int pred = inserts[j];
                    int succ = seqVar.memberAfter(pred);
                    filterDetourForRequired(pred, node, succ, detour(pred, node, succ));
                }
            } else {
                lowerBoundForOptional = lowerBound;
                // updates the min detours for the required nodes: they could involve the optional node
                for (int j = 0 ; j < nRequired ; j++) {
                    int required = this.required[j];
                    int minDetourWithOptional = minDetourForRequiredInvolvingOptional(required, node, nInsert, inserts);
                    if (minDetourWithOptional < minDetourOnRequired[required]) {
                        int diff = minDetourOnRequired[required] - minDetourWithOptional;
                        lowerBoundForOptional -= diff;
                        minDetour[required] = minDetourWithOptional;
                    } else {
                        minDetour[required] = minDetourOnRequired[required];
                    }
                }
                int minDetourOptional = minDetourOnRequired(node, nInsert, inserts);
                minDetour[node] = minDetourOptional;
                if (lowerBoundForOptional + minDetourOptional > totalDist.max()) {
                    seqVar.exclude(node);
                } else {
                    for (int j = 0; j < nInsert; j++) {
                        int pred = inserts[j];
                        int succ = seqVar.memberAfter(pred);
                        filterDetourForOptional(pred, node, succ, detour(pred, node, succ));
                    }
                }
            }
        }
    }

    /**
     * Gives the min detour cost for a required node, computed on detours where
     * - one of the endpoints is a required node
     * - the other endpoint is the optional node
     * This assumes that the value between 0 and {@link DistanceMinDetour#nRequiredInsertable} in
     * {@link DistanceMinDetour#requiredInsertable}
     * correspond to the required insertable nodes from the sequence
     * @param required required node on which the min detour must be computed
     * @param optional one of the endpoint of insertions, that must always be present
     * @param nInsert number of insertions for the optional node
     * @param inserts insertions for the optional node
     * @return min detour cost for the required node
     */
    private int minDetourForRequiredInvolvingOptional(int required, int optional, int nInsert, int[] inserts) {
        int minDetour = Integer.MAX_VALUE;
        // the other endpoint is in the sequence
        for (int i = 0 ; i < nInsert ; i++) {
            int insert = inserts[i];
            int succ = seqVar.memberAfter(insert);
            // checks if the insertion is in common between the required node and the optional node
            if (couldBeDetour(insert, required, optional))
                minDetour = Math.min(minDetour, detour(insert, required, optional)); // detour on insert -> optional
            if (couldBeDetour(optional, required, succ))
                minDetour = Math.min(minDetour, detour(optional, required, succ)); // detour on optional -> succ
        }
        // the other endpoint is a required insertable node
        for (int i = 0 ; i < nRequiredInsertable ; i++) {
            int node = this.requiredInsertable[i];
            if (node != required) {
                if (couldBeDetour(node, required, optional))
                    minDetour = Math.min(minDetour, detour(node, required, optional));
                if (couldBeDetour(optional, required, node))
                    minDetour = Math.min(minDetour, detour(optional, required, node));
            }
        }
        return minDetour;
    }

    /**
     * Gives the min detour cost for a required node, computed on detours where both endpoints are required nodes
     * @param node node on which the min detour must be computed
     * @return
     */
    private int minDetourOnRequired(int node, int nInsert, int[] inserts) {
        int minDetour = Integer.MAX_VALUE;
        // both endpoints are in the sequence
        for (int i = 0 ; i < nInsert ; i++) {
            int insert = inserts[i];
            int succ = seqVar.memberAfter(insert);
            minDetour = Math.min(minDetour, detour(insert, node, succ)); // detour from the sequence
        }
        // at least one endpoint is a required insertable node
        for (int j = 0 ; j < nRequiredInsertable ; j++) {
            int insertable1 = requiredInsertable[j];
            if (insertable1 == node)
                continue;
            // one endpoint in the sequence, one is insertable
            for (int i = 0 ; i < nInsert ; i++) {
                int insert = inserts[i];
                int succ = seqVar.memberAfter(insert);
                if (seqVar.hasEdge(insert, insertable1)) {
                    // a chain insert -> insertable1 -> succ can be formed
                    minDetour = Math.min(minDetour, detour(insert, node, insertable1));  // detour on insert -> insertable1?
                    minDetour = Math.min(minDetour, detour(insertable1, node, succ)); // detour on insertable1 ->succ?
                }
            }
            // both endpoints are required and insertable nodes
            for (int k = j+1 ; k < nRequiredInsertable ; k++) {
                int insertable2 = requiredInsertable[k];
                if (insertable1 == insertable2 || node == insertable2)
                    continue;
                minDetour = Math.min(minDetour, detour(insertable1, node, insertable2));
                minDetour = Math.min(minDetour, detour(insertable2, node, insertable1));
            }
        }
        return minDetour;
    }

    @Override
    public void updateLowerBound() {
        edgeIterator.update();
        int totalMinDetour = 0;

        nRequiredInsertable = seqVar.fillNode(requiredInsertable, INSERTABLE_REQUIRED);
        for (int n = 0; n < nRequiredInsertable; n++) {
            int node = nodes[n];
            int nInsert = seqVar.fillInsert(node, inserts);
            int detour = minDetourOnRequired(node, nInsert, inserts);
            minDetourOnRequired[node] = detour;
            totalMinDetour += detour;
        }

        // remove the lower bound on the total distance
        lowerBound = currentDist + totalMinDetour;
        totalDist.removeBelow(lowerBound);
    }

    public void updateMinDetour(int pred, int node, int succ) {
        int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
        minDetourOnRequired[node] = Math.min(minDetourOnRequired[node], detour);
    }

    private int detour(int pred, int node, int succ) {
        return dist[pred][node] + dist[node][succ] - dist[pred][succ];
    }

    private boolean couldBeDetour(int pred, int node, int succ) {
        return seqVar.hasEdge(pred, node) && seqVar.hasEdge(pred, succ) && seqVar.hasEdge(node, succ);
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {
        if (lowerBound - minDetourOnRequired[node] + detour > totalDist.max()) {
            seqVar.notBetween(pred, node, succ);
        }
    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {
        if (lowerBoundForOptional - minDetour[node] + detour > totalDist.max()) {
            seqVar.notBetween(pred, node, succ);
        }
    }
}
