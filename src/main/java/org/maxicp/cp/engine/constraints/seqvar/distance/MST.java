package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.Arrays;
import java.util.OptionalInt;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;
import static org.maxicp.search.Searches.selectMin;

public class MST {

    private int MSTcost = 0;
    private CPSeqVar seqVar;
    private int[][] cost;
    private boolean[] inMST;
    protected final int[] key; // keep the minimum incoming cost

    protected int[] required;
    protected int nRequired;

    private EdgeIterator edgeIterator;

    public MST(CPSeqVar seqVar, int[][] cost) {
        this.seqVar = seqVar;
        this.cost = cost;
        this.key = new int[seqVar.nNode()];
        inMST = new boolean[seqVar.nNode()];
        this.required = new int[seqVar.nNode()];
        edgeIterator = new SeqvarEdgeIterator(seqVar);
    }

    /**
     * Updates the cost
     */
    public void compute() {
        // Resets the data structures
        edgeIterator.update();
        MSTcost = 0;
        Arrays.fill(inMST, false);
        Arrays.fill(key, Integer.MAX_VALUE);
        // Guarantees to pick the start for the first node of the mst
        key[seqVar.start()] = 0;
        // Will only consider the required node for the MST computation
        nRequired = seqVar.fillNode(required, REQUIRED);
        // TODO could use a sparse-set:
        //  swap elements from the required array so that required[0..nRequired]
        //  only contains nodes that must still be added to the mst
        //  this may provide a slight speedup
        for (int i = 0 ; i < nRequired ; i++) {
            int node = minKey();
            MSTcost += key[node];
            inMST[node] = true;
            for (int j = 0 ; j < nRequired; j++) {
                int other = required[j];
                if (hasEdge(node, other) && !inMST[other] && cost[node][other] < key[other]) {
                    key[other] = cost[node][other];
                }
            }
        }
    }

    private boolean hasEdge(int i, int j) {
        return (edgeIterator.hasEdge(i, j) || edgeIterator.hasEdge(j, i));
    }

    /**
     * Returns the required node with minimum cost, not included in the minimum spanning tree
     */
    private int minKey() {
        OptionalInt node = selectMin(required, nRequired, v -> !inMST[v], v -> key[v]);
        assert node.isPresent();
        return node.getAsInt();
    }

    /**
     * Gives the cost, that must have been updated through {@link MST#compute()}
     * @return cost of the minimum spanning tree
     */
    public int cost() {
        return MSTcost;
    }

}
