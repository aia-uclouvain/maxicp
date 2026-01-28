package org.maxicp.cp.examples.raw.distance;

import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.constraints.seqvar.distance.*;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;

import java.util.Arrays;
import java.util.Random;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.cp.CPFactory.makeIntVar;

public class CreateNonDominatedBoundsMatrix {

    public static void main(String[] args) {


        boolean[][] dominated = new boolean[6][6];


        for (int iter = 0; iter < 1000; iter++) {
            Random rand = new Random(iter);


            int n = 10;

            int[][] dist = new int[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (i != j) {
                        dist[i][j] = rand.nextInt(100);
                    }
                }
            }
            makeTriangularInequality(dist);

            CPSolver cp = makeSolver();
            // route for the traveler
            CPSeqVar tour = makeSeqVar(cp, n, 0, n - 1);
            // all nodes must be visited
            for (int node = 0; node < n; node++) {
                if (node != 3) // one optional node
                    tour.require(node);
            }
            // distance traveled
            CPIntVar totDistance = makeIntVar(cp, 0, n * 100);
            int[] tMin = new int[n];
            int[] tMax = new int[n];
            for (int i = 0; i < n; i++) {
                tMin[i] = tMax[i] = i * 100;
            }
            for (int i = 1; i < n; i++) {
                tMin[i] = Math.max(0, tMin[i] - rand.nextInt(100));
                tMax[i] = tMax[i] + rand.nextInt(100);
            }

            // time at which the departure of each node occurs
            CPIntVar[] time = new CPIntVar[n];
            for (int i = 0; i < n; i++) {
                time[i] = makeIntVar(cp, tMin[i], tMax[i]);
            }

            // time windows
            cp.post(new TransitionTimes(tour, time, dist));
            // tracks the distance over the sequence

            CPConstraint[] distanceConstraints = new CPConstraint[]{
                    new DistanceOriginal(tour, dist, totDistance),
                    new DistanceMSTDetour(tour, dist, totDistance),
                    new DistanceMinDetourSum(tour, dist, totDistance),
                    new DistanceSubsequenceSplit(tour, dist, totDistance),
                    new DistanceMaxInputOrOutputSum(tour, dist, totDistance),
                    new DistanceMinInputAndOutputSum(tour, dist, totDistance),
            };


            for (int i = 0; i < distanceConstraints.length; i++) {
                for (int j = i + 1; j < distanceConstraints.length; j++) {

                    cp.getStateManager().saveState();
                    cp.post(distanceConstraints[i]);
                    int lb_i = totDistance.min();
                    cp.getStateManager().restoreState();

                    cp.getStateManager().saveState();
                    cp.post(distanceConstraints[j]);
                    int lb_j = totDistance.min();
                    cp.getStateManager().restoreState();

                    if (lb_i < lb_j) {
                        dominated[i][j] = true;
                    } else if (lb_j < lb_i) {
                        dominated[j][i] = true;
                    }
                }
            }

        } // end iter

        //print dominance matrix
        for (int i = 0; i < dominated.length; i++) {
            System.out.println(Arrays.toString(dominated[i]));
        }


    }


    public static void makeTriangularInequality(int[][] distance) {
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
