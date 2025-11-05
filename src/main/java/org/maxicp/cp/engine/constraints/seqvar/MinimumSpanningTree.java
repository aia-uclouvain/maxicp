package org.maxicp.cp.engine.constraints.seqvar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MinimumSpanningTree {
    protected final int numNodes;
    protected final int start;
    protected final int[] predsInMST;
    protected final boolean[] inMST;
    protected final int[] key;
    protected final int[][] cost;
    private final boolean[] inserable;

    protected int costMinimumSpanningTree;


    public MinimumSpanningTree(int numNodes, int start, int[][] cost) {
        this.numNodes = numNodes;
        this.start = start;
        this.cost = cost;
        this.predsInMST = new int[numNodes];
        this.inMST = new boolean[numNodes];
        this.key = new int[numNodes];
        this.inserable = new boolean[numNodes];
    }

    public void primMST(int[][] adjacencyMatrix) {
        costMinimumSpanningTree = 0;

        Arrays.fill(predsInMST, -1);
        Arrays.fill(inMST, false);

        // Key values used to pick minimum weight edge in cut
        Arrays.fill(key, Integer.MAX_VALUE);

        key[start] = 0;
        predsInMST[start] = -1;

        for (int i = 0; i < numNodes; i++) {

            // Pick the minimum key vertex from the set of
            // vertices not yet included in MST
            int u = minKey(key, inMST);

            inMST[u] = true;
            for (int v = 0; v < numNodes; v++) {
                if (adjacencyMatrix[u][v] == 1 && !inMST[v] && cost[u][v] < key[v]) {
                    predsInMST[v] = u;
                    key[v] = cost[u][v];
                }
            }

        }
    }

    int minKey(int[] key, boolean[] inMST) {
        // Initialize min value
        int min = Integer.MAX_VALUE;
        int min_index = -1;

        for (int v = 0; v < numNodes; v++) {
            if (!inMST[v] && key[v] < min) {
                min = key[v];
                min_index = v;
            }
        }

        costMinimumSpanningTree += min;
        return min_index;
    }


    public int getCostMinimumSpanningTree() {
        return costMinimumSpanningTree;
    }

    public int[] getPredsInMST() {
        return predsInMST;
    }

}
