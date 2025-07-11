package org.maxicp.cp.engine.constraints;

import java.util.Arrays;

public class MinCostMaxFlow {
    // Stores the found edges
    protected boolean[] found;

    protected int[][] cost;
    protected int[][] capMax;

    protected int[][] flow;
    protected final int numNodes;
    protected final int nunVariables;


    // Stores the distance from each node
    // and picked edges for each node
    protected int[] dad;
    protected int[] dist, pi;

    protected final int INF
            = Integer.MAX_VALUE;
    protected final int H;

    protected int totalCost;
    protected int totalFlow;

    public MinCostMaxFlow(int[][] costNetworkFlow, int[][] capMaxNetworkFlow, int H, int numVariables) {
        this.numNodes = costNetworkFlow.length;
        this.nunVariables = numVariables;
        this.cost = costNetworkFlow;
        this.capMax = capMaxNetworkFlow;
        this.H = H;
    }

    public boolean run(int source, int dest) {
        int[] ret = getMaxFlowByMaxCapacity(source, dest, numNodes);

        totalFlow = ret[0];
        totalCost = ret[1];

        return true;
    }


    // Function to check if it is possible to
    // have a flow from the src to sink
    protected boolean searchByMaxCapacity(int source, int dest) {

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
    protected int[] getMaxFlowByMaxCapacity(int source, int dest, int maxFlowAuthorized) {
        found = new boolean[numNodes];
        flow = new int[numNodes][numNodes];
        dist = new int[numNodes + 1];
        dad = new int[numNodes];
        pi = new int[numNodes];

        int totflow = 0, totcost = 0;

        // If a path exist from src to sink
        while (searchByMaxCapacity(source, dest)) {

            // Set the default amount
            int amt = INF;
            for (int x = dest; x != source; x = dad[x])

                amt = Math.min(amt,
                        flow[x][dad[x]] != 0
                                ? flow[x][dad[x]]
                                : capMax[dad[x]][x]
                                - flow[dad[x]][x]);

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
        return new int[]{totflow, totcost};
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
}
