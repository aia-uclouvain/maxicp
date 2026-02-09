package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.OptionalInt;
import java.util.stream.IntStream;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.selectMin;

/**
 * Uses a minRestrictedDetour for computing the bound, and a slow (but loyal) filtering for the insertions,
 * by forcing a node as first within the bound estimation and looking it this leads to an overestimation of the bound
 */
public class DistanceMinRestrictedDetourShaving extends AbstractCPConstraint {

    private EdgeIterator edgeIterator;
    private BoundEstimator estimator;

    private CPSeqVar seqVar;
    protected final CPIntVar totalDist;
    private int[][] dist;

    protected int[] member;
    protected final int[] nodes;
    protected final int[] inserts;
    protected int nMember;
    protected int costPartialPath;

    public DistanceMinRestrictedDetourShaving(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar.getSolver());
        this.seqVar = seqVar;
        this.dist = dist;
        this.totalDist = totalDist;
        estimator = new BoundEstimator();
        this.member = new int[seqVar.nNode()];
        this.nodes = new int[seqVar.nNode()];
        this.inserts = new int[seqVar.nNode()];
        edgeIterator = new SeqvarEdgeIterator(seqVar);
    }

    @Override
    public void post() {
        seqVar.propagateOnInsertRemoved(this);
        seqVar.propagateOnInsert(this);
        seqVar.propagateOnFix(this);
        totalDist.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        edgeIterator.update();
        costPartialPath = 0;
        // Initialize with the edges from the current sequence
        nMember = seqVar.fillNode(member, MEMBER_ORDERED);
        for (int i = 0; i < nMember - 1; i++) {
            int node = member[i];
            int succ = member[i + 1];
            costPartialPath += dist[node][succ];
        }
        if (seqVar.isFixed()) {
            totalDist.fix(costPartialPath);
            setActive(false);
            return;
        } else {
            // first naive lower bound
            totalDist.removeBelow(costPartialPath);
            // more refined lower bound
            estimator.prepareForRequired();
            estimator.addInitialDetours();
            int cost = estimator.compute(-1);
            totalDist.removeBelow(cost);
        }
        // filter for all insertable nodes
        int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
        for (int i = 0 ; i < nInsertable ; i++) {
            int node = nodes[i];
            // check what is the cost of the cheapest insertion when forcing this node within the sequence
            estimator.prepareForRequired();
            int id;
            if (seqVar.isNode(node, POSSIBLE)) {
                // force a possible node, by temporarily considering that it is required
                estimator.addNode(node);
                id = estimator.nRequiredRemaining - 1; // position of the node within estimator.required
            } else {
                // position of the node within estimator.required
                id = estimator.idOf(node);
            }
            estimator.addInitialDetours();
            int minDetourCost = estimator.minDetour[node];
            // cost if forcing the node to be connected with its minimum detour
            int costIfConnected = estimator.compute(id);
            if (costIfConnected > totalDist.max()) {
                seqVar.exclude(node);
            } else {
                int nPreds = seqVar.fillInsert(node, inserts);
                for (int p = 0; p < nPreds; p++) {
                    int pred = inserts[p];
                    int succ = seqVar.memberAfter(pred);
                    int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
                    int cost = costIfConnected - minDetourCost + detour;
                    if (cost > totalDist.max()) {
                        seqVar.notBetween(pred, node, succ);
                    }
                }
            }
            if (seqVar.nNode(MEMBER) != nMember) {
                return; // need to trigger again the constraint, as a node was inserted
            }
        }
    }

    private class BoundEstimator {

        protected final int[] minDetour; // keep the minimum incoming cost
        protected final int[] indices;

        protected int[] required;
        protected int nRequiredRemaining;

        public BoundEstimator() {
            this.minDetour = new int[seqVar.nNode()];
            this.required = new int[seqVar.nNode()];
            indices = IntStream.range(0, seqVar.nNode()).toArray();
        }

        public void prepareForRequired() {
            nRequiredRemaining = seqVar.fillNode(required, INSERTABLE_REQUIRED);
        }

        public void addNode(int node) {
            required[nRequiredRemaining] = node;
            nRequiredRemaining += 1;
        }

        public void addInitialDetours() {
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
         * Gives a cost estimate to connect all nodes within required[0..nRequiredRemaining]
         * @param idToForce index of the node to insert first within the required[..] array.
         *                  Using -1 fallbacks to the default selection for the first node
         * @return
         */
        public int compute(int idToForce) {
            int cost = costPartialPath;
            if (nRequiredRemaining > 0) {
                int nRequiredRemainingInit = nRequiredRemaining;
                // required[0..nRequiredRemaining] = insertable nodes that must be added to the MST
                // required[nRequiredRemaining..nRequiredRemainingInit] = insertable nodes that have already been added to the MST
                while (nRequiredRemaining > 0) {
                    int id;
                    if (idToForce != -1) {
                        id = idToForce;
                        idToForce = -1;
                    } else {
                        id = maxKeyIdx(nRequiredRemaining);
                    }
                    // selected the node at required[id]
                    // swap it with the node at required[nRequiredRemaining-1]
                    // this way the nodes that are not belonging to the MST are only located in required[0..nRequiredRemaining]
                    int toSwapIdx = nRequiredRemaining - 1;
                    int newlyInserted = required[id];
                    required[id] = required[toSwapIdx];
                    required[toSwapIdx] = newlyInserted;
                    nRequiredRemaining--;

                    cost += minDetour[newlyInserted];
                    for (int j = 0; j < nRequiredRemaining; j++) {
                        int toInsert = required[j]; // considers the newly formed link to insert this node
                        for (int i = 0; i < nMember - 1; i++) {
                            int pred = member[i];
                            int succ = member[i + 1];
                            // consider that an insertion pred -> newlyInserted -> succ happened
                            if (seqVar.hasInsert(pred, toInsert) && seqVar.hasInsert(pred, newlyInserted)) {
                                updateCostFor(pred, toInsert, newlyInserted); // try to insert on pred -> newlyInserted
                                updateCostFor(newlyInserted, toInsert, succ); // try to insert on newlyInserted -> succ
                                for (int k = nRequiredRemaining; k < nRequiredRemainingInit; k++) {
                                    // insertable node that has already been added to the MST at a previous iteration
                                    int previouslyInserted = required[k];
                                    if (previouslyInserted != newlyInserted) {
                                        if (seqVar.hasEdge(pred, previouslyInserted)) {
                                            // a chain pred -> previouslyInserted -> succ can be formed
                                            // try to insert on newlyInserted -> toInsert -> previouslyInserted
                                            updateCostFor(newlyInserted, toInsert, previouslyInserted);
                                            // try other direction: insert on previouslyInserted -> toInsert -> newlyInserted
                                            updateCostFor(previouslyInserted, toInsert, newlyInserted);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return cost;
        }

        private void updateCostFor(int pred, int node, int succ) {
            int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
            if (detour < minDetour[node]) {
                minDetour[node] = detour;
            }
        }

        /**
         * Returns the required node with maximum cost, not included in the minimum spanning tree
         */
        private int maxKeyIdx(int n) {
            OptionalInt idx = selectMin(indices, n, i -> true, i -> -minDetour[required[i]]);
            assert idx.isPresent();
            return idx.getAsInt();
        }

        private int idOf(int node) {
            for (int i = 0; i < required.length; i++) {
                int n = required[i];
                if (n == node) {
                    return i;
                }
            }
            return -1;
        }

    }

}
