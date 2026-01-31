package org.maxicp.util.algo;


import java.util.Arrays;

public class DistanceMatrix {


    /**
     * Enforces the triangular inequality on the given distance
     * matrix using the Bellman-Ford algorithm.
     * The shortest paths are computed between all pairs of nodes and
     * the distance matrix is updated accordingly.
     * @param distance
     */
    public static void enforceTriangularInequality(int[][] distance) {
        int n = distance.length;
        int[][] edges = new int[n * n][3];
        int edgeCount = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                edges[edgeCount][0] = i;
                edges[edgeCount][1] = j;
                edges[edgeCount][2] = distance[i][j];
                edgeCount++;
            }
        }
        for (int i = 0; i < n; i++) {
            long[] dist = new long[n];
            try {
                bellmanFord(n, edgeCount, edges, i, dist);
                for (int j = 0; j < n; j++) {
                    distance[i][j] = (int) dist[j];
                }
            } catch (Exception e) {
                System.out.println("negative cycle");
            }
        }
    }

    private static void bellmanFord(int numNodes, int edgeCount, int[][] edges, int src, long[] dist) throws Exception {
        // Initially distance from source to all other vertices
        // is not known(Infinite).
        int INF = Integer.MAX_VALUE;
        Arrays.fill(dist, INF);
        dist[src] = 0;
        // Relaxation of all the edges V times, not (V - 1) as we
        // need one additional relaxation to detect negative cycle
        for (int i = 0; i < numNodes; i++) {
            for (int ne = 0; ne < edgeCount; ne++) {
                int u = edges[ne][0];
                int v = edges[ne][1];
                int wt = edges[ne][2];
                if (dist[u] != INF && dist[u] + wt < dist[v]) {
                    // V_th relaxation => negative cycle
                    if (i == numNodes - 1) {
                        throw new Exception();
                    }
                    // Update shortest distance to node v
                    dist[v] = dist[u] + wt;
                }
            }
        }
    }

}
