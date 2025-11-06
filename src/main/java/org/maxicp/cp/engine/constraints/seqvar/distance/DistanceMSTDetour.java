package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.OptionalInt;
import java.util.stream.IntStream;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.selectMin;

public class DistanceMSTDetour extends AbstractDistance {

    private MST minimumSpanningTree;

    public DistanceMSTDetour(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        int[][] costForMST = new int[nNodes][nNodes];
        for (int i = 0; i < nNodes; i++) {
            for (int j = i; j < nNodes; j++) {
                costForMST[i][j] = Math.min(dist[i][j], dist[j][i]); // ensure symmetry
                costForMST[j][i] = costForMST[i][j]; // ensure symmetry
            }
        }
        this.minimumSpanningTree = new MST(seqVar, costForMST);
    }

    @Override
    public void updateLowerBound() {
        minimumSpanningTree.compute();
        totalDist.removeBelow(minimumSpanningTree.cost());
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {

    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {

    }

    private class MST {

        private int MSTcost = 0;
        private CPSeqVar seqVar;
        private int[][] cost;
        protected final int[] minDetour; // keep the minimum incoming cost
        protected final int[] indices;

        protected int[] required;
        protected int[] member;
        protected int nMember;

        public MST(CPSeqVar seqVar, int[][] cost) {
            this.seqVar = seqVar;
            this.cost = cost;
            this.minDetour = new int[seqVar.nNode()];
            this.required = new int[seqVar.nNode()];
            this.member = new int[seqVar.nNode()];
            indices = IntStream.range(0, seqVar.nNode()).toArray();
        }

        private void addInitialDetours(int nRequiredRemaining) {
            for (int i = 0; i < nRequiredRemaining; i++) {
                int node = required[i];
                minDetour[node] = Integer.MAX_VALUE;
                for (int j = 0 ; j < nMember - 1 ; j++) {
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
        public void compute() {
            // Resets the data structures
            MSTcost = 0;
            // Initialize with the edges from the current sequence
            nMember = seqVar.fillNode(member, MEMBER_ORDERED);
            for (int i = 0 ; i < nMember - 1 ; i++) {
                int node = member[i];
                int succ = member[i + 1];
                MSTcost += cost[node][succ];
            }
            int nRequiredRemaining = seqVar.fillNode(required, INSERTABLE_REQUIRED);
            if (nRequiredRemaining > 0) {
                // add the first detours that can be used
                addInitialDetours(nRequiredRemaining);
                int nRequiredRemainingInit = nRequiredRemaining;
                // required[0..nRequiredRemaining] = insertable nodes that must be added to the MST
                // required[nRequiredRemaining..nRequiredRemainingInit] = insertable nodes that have already been added to the MST
                while (nRequiredRemaining > 0) {
                    int id = minKeyIdx(nRequiredRemaining);
                    // selected the node at required[id]
                    // swap it with the node at required[nRequiredRemaining-1]
                    // this way the nodes that are not belonging to the MST are only located in required[0..nRequiredRemaining]
                    int toSwapIdx = nRequiredRemaining - 1;
                    int newlyInserted = required[id];
                    required[id] = required[toSwapIdx];
                    required[toSwapIdx] = newlyInserted;
                    nRequiredRemaining--;

                    MSTcost += minDetour[newlyInserted];
                    for (int j = 0 ; j < nRequiredRemaining ; j++) {
                        int toInsert = required[j]; // considers the newly formed link to insert this node
                        for (int i = 0 ; i < nMember - 1 ; i++) {
                            int pred = member[i];
                            int succ = member[i + 1];
                            // consider that an insertion pred -> newlyInserted -> succ happened
                            if (canConnect(pred, toInsert, succ) && canConnect(pred, newlyInserted, succ)) {
                                updateCostFor(pred, toInsert, newlyInserted); // try to insert on pred -> newlyInserted
                                updateCostFor(newlyInserted, toInsert, succ); // try to insert on newlyInserted -> succ
                            }
                            for (int k = nRequiredRemaining ; k < nRequiredRemainingInit ; k++) {
                                int inserted = required[k];
                                updateCostFor(newlyInserted, toInsert, inserted);
                                updateCostFor(inserted, toInsert, newlyInserted);
                            }
                        }
                    }
                }
            }
        }

        private void updateCostFor(int pred, int node, int succ) {
            int detour = this.cost[pred][node] + this.cost[node][succ] - this.cost[pred][succ];
            if (detour < minDetour[node]) {
                minDetour[node] = detour;
            }
        }

        private boolean canConnect(int pred, int node, int succ) {
            return seqVar.hasInsert(pred, node);
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
         * Gives the cost, that must have been updated through {@link MST#compute()}
         * @return cost of the minimum spanning tree
         */
        public int cost() {
            return MSTcost;
        }

    }
}
