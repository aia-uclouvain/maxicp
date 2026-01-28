package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.OptionalInt;
import java.util.stream.IntStream;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.selectMin;

public class DistanceMSTDetour extends AbstractDistance {

    private EdgeIterator edgeIterator;

    private int lowerBound = 0;
    private CPSeqVar seqVar;
    private int[][] dist;
    protected final int[] minDetour; // keep the minimum incoming cost
    protected final int[] indices;

    protected int[] required;
    protected int nRequiredRemaining;
    protected int[] member;
    protected int nMember;

    public DistanceMSTDetour(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        this.seqVar = seqVar;
        this.dist = dist;
        this.minDetour = new int[seqVar.nNode()];
        this.required = new int[seqVar.nNode()];
        this.member = new int[seqVar.nNode()];
        indices = IntStream.range(0, seqVar.nNode()).toArray();
        edgeIterator = new SeqvarEdgeIterator(seqVar);
    }

    @Override
    public void updateLowerBound() {
        edgeIterator.update();
        computeLowerBound();
        totalDist.removeBelow(lowerBound);
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {

        // This filters but apparently creates larger search trees to solve problems?
        // It is slow, seems better to remove it anyway :-)
        int mstKey = minDetour[node];
        int minDetour = minDetourBetween(pred, node, succ);
        if (lowerBound - mstKey + minDetour > totalDist.max()) {
            seqVar.notBetween(pred, node, succ);
        }

    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {
        // This filters but apparently creates larger search trees to solve problems?
        // It is slow, seems better to remove it anyway :-)
        int minDetour = minDetourBetween(pred, node, succ);
        if (lowerBound + minDetour > totalDist.max())
            seqVar.notBetween(pred, node, succ);
    }

    /**
     * Computes an estimate of the minimum detour between two nodes belonging to the sequence.
     * This takes into account future detours where other nodes could be put on the edge (pred, succ)
     *
     * @param pred predecessor of the insertion
     * @param node node to insert
     * @param succ successor of the insertion
     */
    private int minDetourBetween(int pred, int node, int succ) {
        int minDetour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
        int[] dummy = new int[seqVar.nNode(INSERTABLE_REQUIRED)];
        int nRequiredInsertable = seqVar.fillNode(dummy, INSERTABLE_REQUIRED);
        for (int i = 0; i < nRequiredInsertable; i++) {
            int inserted = dummy[i];
            if (seqVar.hasEdge(pred, inserted) && inserted != node) {
                int detour1 = dist[pred][node] + dist[node][inserted] - dist[pred][inserted];
                int detour2 = dist[inserted][node] + dist[node][succ] - dist[inserted][succ];
                minDetour = Math.min(minDetour, Math.min(detour1, detour2));
                for (int j = i + 1; j < nRequiredInsertable; j++) {
                    int inserted2 = dummy[j];
                    if (seqVar.hasEdge(pred, inserted2) && inserted2 != node) {
                        detour1 = dist[inserted][node] + dist[node][inserted2] - dist[inserted][inserted2];
                        detour2 = dist[inserted2][node] + dist[node][inserted] - dist[inserted2][inserted];
                        minDetour = Math.min(minDetour, Math.min(detour1, detour2));
                    }
                }
            }
        }
        return minDetour;
    }

    private void addInitialDetours(int nRequiredRemaining) {
        for (int i = 0; i < nRequiredRemaining; i++) {
            int node = required[i];
            minDetour[node] = Integer.MAX_VALUE;
            for (int j = 0; j < nMember - 1; j++) {
                int pred = member[j];
                int succ = member[j + 1];
                if (seqVar.hasInsert(pred, node)) {
                    updateCostFor(pred, node, succ);
                }
            }
        }
    }

    /**
     * Updates the cost
     */
    public void computeLowerBound() {
        // Resets the data structures
        lowerBound = 0;
        // Initialize with the edges from the current sequence
        nMember = seqVar.fillNode(member, MEMBER_ORDERED);
        for (int i = 0; i < nMember - 1; i++) {
            int node = member[i];
            int succ = member[i + 1];
            lowerBound += dist[node][succ];
        }
        nRequiredRemaining = seqVar.fillNode(required, INSERTABLE_REQUIRED);
        if (nRequiredRemaining > 0) {
            // add the first detours that can be used
            addInitialDetours(nRequiredRemaining);
            int nRequiredRemainingInit = nRequiredRemaining;
            // required[0..nRequiredRemaining] = insertable nodes that must be added to the MST
            // required[nRequiredRemaining..nRequiredRemainingInit] = insertable nodes that have already been added to the MST
            while (nRequiredRemaining > 0) {
                int id = maxKeyIdx(nRequiredRemaining); //minKeyIdx(nRequiredRemaining);
//                     selected the node at required[id]
                // swap it with the node at required[nRequiredRemaining-1]
                // this way the nodes that are not belonging to the MST are only located in required[0..nRequiredRemaining]
                int toSwapIdx = nRequiredRemaining - 1;
                int newlyInserted = required[id];
                required[id] = required[toSwapIdx];
                required[toSwapIdx] = newlyInserted;
                nRequiredRemaining--;

                lowerBound += minDetour[newlyInserted];
                for (int j = 0; j < nRequiredRemaining; j++) {
                    int toInsert = required[j]; // considers the newly formed link to insert this node
                    for (int i = 0; i < nMember - 1; i++) {
                        int pred = member[i];
                        int succ = member[i + 1];
                        // consider that an insertion pred -> newlyInserted -> succ happened
                        if (seqVar.hasInsert(pred, toInsert) && seqVar.hasInsert(pred, newlyInserted)) {
                            updateCostFor(pred, toInsert, newlyInserted); // try to insert on pred -> newlyInserted
                            updateCostFor(newlyInserted, toInsert, succ); // try to insert on newlyInserted -> succ
                        }
                    }
                    for (int k = nRequiredRemaining; k < nRequiredRemainingInit; k++) {
                        // insertable node that has already been added to the MST at a previous iteration
                        int previouslyInserted = required[k];
                        if (previouslyInserted != newlyInserted) {
                            // for correctness, this should be added into the inner loop that checks the edges between
                            // pred -> succ. But it provides so little difference that this can be put outside instead
                            // more links which will be examined than necessary, which slightly worsen the bound,
                            // but the speed is increased
                            //if (seqVar.hasEdge(pred, previouslyInserted)) {
                            // a chain pred -> previouslyInserted -> succ can be formed
                            // try to insert on newlyInserted -> toInsert -> previouslyInserted
                            updateCostFor(newlyInserted, toInsert, previouslyInserted);
                            // try other direction: insert on previouslyInserted -> toInsert -> newlyInserted
                            updateCostFor(previouslyInserted, toInsert, newlyInserted);
                            //}
                        }
                    }
                }
            }
        }
    }

    private void updateCostFor(int pred, int node, int succ) {
        int detour = this.dist[pred][node] + this.dist[node][succ] - this.dist[pred][succ];
        if (detour < minDetour[node]) {
            minDetour[node] = detour;
        }
    }

    /**
     * Returns the required node with minimum cost, not included in the minimum spanning tree
     */
    private int minKeyIdx(int n) {
        OptionalInt idx = selectMin(indices, n, i -> true, i -> minDetour[required[i]]);
        assert idx.isPresent();
        return idx.getAsInt();
    }

    /**
     * Returns the required node with maximum cost, not included in the minimum spanning tree
     */
    private int maxKeyIdx(int n) {
        OptionalInt idx = selectMin(indices, n, i -> true, i -> -minDetour[required[i]]);
        assert idx.isPresent();
        return idx.getAsInt();
    }
}
