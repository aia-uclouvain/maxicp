package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.modeling.algebra.sequence.SeqStatus;

import java.util.Arrays;

public class MSTDetour {

    /**
     * Creates a minimum spanning tree over a set of n nodes
     * @param n number of nodes on which the minimum spanning tree is applied
     */
    public MSTDetour(int n) {
        this.n = n;
        this.nodes = new int[n];
        this.preds = new int[n];
        this.predWhenInserted = new int[n];
        this.succWhenInserted = new int[n];
        this.minCost = new int[n];
        this.inMst = new boolean[n];
        this.adjacency = new boolean[n][n];
    }

    int n;
    int[] nodes; // used for fill operations
    int[] preds; // used for fill operations
    int[] predWhenInserted; // predecessor of a node when added to the MST
    int[] succWhenInserted; // successor   of a node when added to the MST
    int[] minCost; // minimum cost for adding a node (detour cost)
    boolean[] inMst; // tells if a node is within the MST
    boolean[][] adjacency; // adjacency[i][j] = node j can be put after node i

    /**
     * Computes the cost of the minimum spanning tree, using Prim's algorithm and detour cost
     * @param seqVar sequence on which the minimum spanning tree cost must be computed
     * @param distMatrix distance between nodes
     * @return cost of the minimum spanning tree
     */
    public int compute(CPSeqVar seqVar, int[][] distMatrix) {
        Arrays.fill(minCost, Integer.MAX_VALUE);
        int MSTcost = initFromCurrentPath(seqVar, distMatrix); // cost from the current path
        int nInsertable = seqVar.fillNode(nodes, SeqStatus.INSERTABLE);
        for (int i = 0 ; i < n ; i++) {
            Arrays.fill(adjacency[i], false);
        }
        // connects all insertable nodes to the sequence
        for (int i = 0 ; i < nInsertable ; i++) {
            int addedToMST = minKey();
            // adds the node to the MST. (same as performing an insertion pred -> node -> succ)
            MSTcost += minCost[addedToMST];
            inMst[addedToMST] = true;
            // update other costs with the newly formed detour that appeared
            for (int j = 0 ; j < nInsertable ; j++) {
                int node = nodes[j];
                if (inMst[node]) {
                    continue;
                }
                // may only insert newNode if pred -> succ was already a valid insertion place for it
                //if (adjacency[predWhenInserted[node]][newNode]) {
                    adjacency[addedToMST][node] = true;
                    int[] preds = new int[] {predWhenInserted[addedToMST], addedToMST};
                    int[] succs = new int[] {addedToMST, succWhenInserted[addedToMST]};
                    for (int k = 0 ; k < 2 ; k++) {
                        int pred = preds[k];
                        int succ = succs[k];
                        int cost = distMatrix[pred][node] + distMatrix[node][succ] - distMatrix[pred][succ];
                        if (cost < minCost[node]) {
                            minCost[node] = cost;
                            predWhenInserted[node] = pred;
                            succWhenInserted[node] = succ;
                        }
                    }
                //}
            }
        }
        return MSTcost;
    }

    /**
     * Computes the cost of the current path, and initialize the MST data (initial cheapest costs, ...)
     * @param seqVar sequence on which the MST is applied
     * @param distMatrix distance between nodes
     * @return cost of the current path
     */
    private int initFromCurrentPath(CPSeqVar seqVar, int[][] distMatrix) {
        int MSTcost = 0;
        // cost of the current tour
        int nMember = seqVar.fillNode(nodes, SeqStatus.MEMBER_ORDERED);
        for (int i = 0 ; i < nMember - 1 ; i++) {
            int pred = nodes[i];
            int succ = nodes[i + 1];
            inMst[pred] = true;
            MSTcost += distMatrix[pred][succ];
            adjacency[pred][succ] = true;
        }
        inMst[seqVar.end()] = true;
        // compute the links between nodes
        int nInsertable = seqVar.fillNode(nodes, SeqStatus.INSERTABLE);
        for  (int i = 0 ; i < nInsertable ; i++) {
            int node =  nodes[i];
            inMst[node] = false;
            int nPred = seqVar.fillInsert(node, preds);
            for (int j = 0 ; j < nPred ; j++) {
                int pred = preds[j];
                adjacency[pred][node] = true;
                int succ = seqVar.memberAfter(pred);
                int cost = distMatrix[pred][node] + distMatrix[node][succ] - distMatrix[pred][succ];
                if (cost < minCost[node]) {
                    minCost[node] = cost;
                    predWhenInserted[node] = pred;
                    succWhenInserted[node] = succ;
                }
            }
        }
        return MSTcost;
    }

    /**
     * Gives the node with minimum detour insertion cost
     * @return node with minimum detour insertion cost
     */
    private int minKey() {
        // Yes this is not using a priority queue at the moment, not needed at this point :-)
        int min = Integer.MAX_VALUE;
        int bestNode = -1;
        for (int node = 0; node < n ; node++) {
            if (!inMst[node] && minCost[node] < min) {
                min = minCost[node];
                bestNode = node;
            }
        }
        return bestNode;
    }


}
