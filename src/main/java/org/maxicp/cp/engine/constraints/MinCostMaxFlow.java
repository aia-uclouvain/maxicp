package org.maxicp.cp.engine.constraints;

import java.util.Arrays;

public class MinCostMaxFlow {
    // Stores the found edges
    private final boolean[] found;

    private int[][] cost;
    private int[][] capMax;

    private final int[][] flow;
    private final int numNodes;


    // Stores the distance from each node
    // and picked edges for each node
    private final int[] dad;
    private final int[] dist, pi;

    private final int INF
            = Integer.MAX_VALUE;
    private int H;

    private int totalCost;
    private int totalFlow;

    private final int[] result = new int[2];

    private boolean linkedDefined;
    private int[] linkedPred;
    private int[] linkedSucc;

    public MinCostMaxFlow(int numNodes) {
        this.numNodes = numNodes;

        this.found = new boolean[numNodes];
        this.flow = new int[numNodes][numNodes];
        this.dist = new int[numNodes + 1];
        this.dad = new int[numNodes];
        this.pi = new int[numNodes];
        this.linkedPred = new int[numNodes];
        this.linkedSucc = new int[numNodes];
    }

    public boolean run(int H, int source, int dest, int[][] capMaxNetworkFlow, int[][] costNetworkFlow) {
        this.H = H;
        this.cost = costNetworkFlow;
        this.capMax = capMaxNetworkFlow;
        linkedDefined = false;
        Arrays.fill(linkedPred, -1);
        Arrays.fill(linkedSucc, -1);
        getMaxFlowByMaxCapacity(source, dest, numNodes);

        totalFlow = result[0];
        totalCost = result[1];

        return true;
    }


    // Function to check if it is possible to
    // have a flow from the src to sink
    private boolean searchByMaxCapacity(int source, int dest) {
        // Initialise found[] to false
        Arrays.fill(found, false);

        // Initialise the dist[] to INF
        Arrays.fill(dist, INF);


        // Distance from the source node
        dist[source] = 0;

        // Iterate until src reaches N
        while (source != numNodes) {

            int best = numNodes;
            found[source] = true;

            for (int k = 0; k < numNodes; k++) {

                // If already found
                if (found[k])
                    continue;

                // Evaluate while flow
                // is still in supply
                if (flow[k][source] != 0) {

                    // Obtain the total value
                    int val
                            = (int) (dist[source] + pi[source]
                            - pi[k] - cost[k][source]);

                    // If dist[k] is > minimum value
                    if (dist[k] > val) {

                        // Update
                        dist[k] = val;
                        dad[k] = source;
                    }
                }

                if (flow[source][k] < capMax[source][k]) {
                    int val = (int) (dist[source] + pi[source]
                            - pi[k] + cost[source][k]);

                    // If dist[k] is > minimum value
                    if (dist[k] > val) {

                        // Update
                        dist[k] = val;
                        dad[k] = source;
                    }
                }

                if (dist[k] < dist[best])
                    best = k;
            }

            // Update src to best for
            // next iteration
            source = best;
        }

        for (int k = 0; k < numNodes; k++)
            pi[k]
                    = Math.min(pi[k] + dist[k],
                    INF);

        // Return the value obtained at sink
        return found[dest];
    }

    // Function to obtain the maximum Flow
    private void getMaxFlowByMaxCapacity(int source, int dest, int maxFlowAuthorized) {
        Arrays.fill(found, false);
        Arrays.fill(dist, 0);
        Arrays.fill(dad, 0);
        Arrays.fill(pi, 0);

        for (int k = 0; k < numNodes; k++) {
            Arrays.fill(flow[k], 0);
        }


        int totflow = 0, totcost = 0;

        // If a path exist from src to sink
        while (searchByMaxCapacity(source, dest)) {

            // Set the default amount
            int amt = 1; //INF;
//            for (int x = dest; x != source; x = dad[x])
//
//                amt = Math.min(amt,
//                        flow[x][dad[x]] != 0
//                                ? flow[x][dad[x]]
//                                : capMax[dad[x]][x]
//                                - flow[dad[x]][x]);

            for (int x = dest; x != source; x = dad[x]) {

                if (flow[x][dad[x]] != 0) {
                    flow[x][dad[x]] -= amt;
                    totcost -= amt * cost[x][dad[x]];
                } else {
                    flow[dad[x]][x] += amt;
                    totcost += amt * cost[dad[x]][x];
                }
            }
            totflow += amt;

            if (totflow == maxFlowAuthorized) {
                break;
            }
        }

        // Return pair total cost and sink
        result[0] = totflow;
        result[1] = totcost;
//        return result;
    }

    public int getTotalCost() {
        return totalCost;
    }

    public int getTotalFlow() {
        return totalFlow;
    }

    public int[][] getFlow() {
        return flow;
    }

    private void initLinks() {
        linkedDefined = true;
        for (int i = 1; i < numNodes-1; i++) {
            for (int k = 1; k < numNodes-1; k++) {
                if (flow[i][k] != 0) {
                    linkedPred[k] = i;
                    linkedSucc[i] = k;
                }
            }
        }
    }

    public int[] getLinkedPred() {
        if (!linkedDefined) {
            initLinks();
        }
        return linkedPred;

    }

    public int[] getLinkedSucc() {
        if (!linkedDefined) {
            initLinks();
        }
        return linkedSucc;
    }
}
