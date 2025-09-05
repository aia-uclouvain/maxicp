package org.maxicp.cp.engine.constraints.seqvar;

import java.util.Arrays;

public class MinimumSpanningTreeDetour {
    protected final int numNodes;
    protected final int start;
    protected final int[] predsInMST;
    protected final boolean[] inMST;
    protected final int[] key;
    protected final int[][] cost;

    protected int costMinimumSpanningTree;


    public MinimumSpanningTreeDetour(int numNodes, int start, int[][] cost) {
        this.numNodes = numNodes;
        this.start = start;
        this.cost = cost;
        this.predsInMST = new int[numNodes];
        this.inMST = new boolean[numNodes];
        this.key = new int[numNodes];
    }

    public void primMST(int[][] adjacencyMatrix, int[] succInSeq, int nMember, int[] nodes) {
        costMinimumSpanningTree = 0;

        Arrays.fill(predsInMST, -1);
        Arrays.fill(inMST, false);

        // Key values used to pick minimum weight edge in cut
        Arrays.fill(key, Integer.MAX_VALUE);

        predsInMST[start] = -1;

        for (int i = 0; i < nMember; i++) {
            int u = nodes[i];

            inMST[u] = true;
            if (succInSeq[u] == start) {
                continue;
            }
            costMinimumSpanningTree += cost[u][succInSeq[u]];

            for (int v = 0; v < numNodes; v++) {
                if (adjacencyMatrix[u][v] == 1 && !inMST[v]) {
                    int detour = cost[u][v] + cost[v][succInSeq[u]] - cost[u][succInSeq[u]];
                    if (detour < key[v]) {
                        predsInMST[v] = u;
                        key[v] = detour;
                    }
                }


            }
        }

            for (int i = 0; i < numNodes - nMember; i++) {

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
