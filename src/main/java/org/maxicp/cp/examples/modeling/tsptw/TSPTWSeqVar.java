/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling.tsptw;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.examples.utils.TSPTWInstance;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.TimeIt;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * Traveling Salesman Problem with Time Windows (TSPTW) using a sequence variable model.
 *
 * <p>Model:
 * <ul>
 *   <li>{@link SeqVar} representing the path from depot to its duplicate</li>
 *   <li>{@link Factory#transitionTimes} linking time windows and distances</li>
 *   <li>{@link Factory#distance} for the total distance objective</li>
 * </ul>
 *
 * <p>Instances from: https://lopez-ibanez.eu/tsptw-instances
 */
public class TSPTWSeqVar {

    public static void main(String[] args) {

        TSPTWInstance instance = new TSPTWInstance("data/TSPTW/Dumas/n40w20.001.txt");
        int n = instance.n;
        int[][] dist = instance.distMatrix;

        ModelDispatcher model = Factory.makeModelDispatcher();

        // Sequence variable: path from depot (0) to depot-duplicate (n-1)
        SeqVar tour = model.seqVar(n, 0, n - 1);

        // All nodes must be visited
        for (int i = 0; i < n; i++) {
            model.add(require(tour, i));
        }

        // Time window variables
        IntExpression[] time = new IntExpression[n];
        time[0] = model.intVar(0, 0); // depot at time 0
        for (int i = 1; i < n; i++) {
            time[i] = model.intVar(instance.earliest[i], instance.latest[i]);
        }

        // Link time windows and distances
        model.add(transitionTimes(tour, time, dist));

        // Total distance
        int distUB = org.maxicp.util.Arrays.max(dist) * n;
        IntVar totalDist = model.intVar(0, distUB);
        model.add(distance(tour, dist, totalDist));

        long t = TimeIt.run(() -> model.runCP((cp) -> {
            int[] nodes = new int[n];
            DFSearch dfs = cp.dfSearch(() -> {
                if (tour.isFixed())
                    return EMPTY;
                int nUnfixed = tour.fillNode(nodes, INSERTABLE);
                int node = Searches.selectMin(nodes, nUnfixed, i -> true, tour::nInsert).getAsInt();
                int nInsert = tour.fillInsert(node, nodes);
                int bestPred = Searches.selectMin(nodes, nInsert, pred -> true,
                        pred -> {
                            int succ = tour.memberAfter(node);
                            return dist[pred][node] + dist[node][succ] - dist[pred][succ];
                        }).getAsInt();
                int succ = tour.memberAfter(bestPred);
                return branch(() -> cp.getModelProxy().add(Factory.insert(tour, bestPred, node)),
                        () -> cp.getModelProxy().add(Factory.notBetween(tour, bestPred, node, succ)));
            });

            dfs.onSolution(() -> {
                System.out.println("tour: " + tour);
                System.out.println("total distance: " + totalDist);
            });

            SearchStatistics stats = dfs.optimize(minimize(totalDist));
            System.out.println(stats);
        }));

        System.out.println("Time (s): " + (t / 1_000_000_000.));
    }
}
