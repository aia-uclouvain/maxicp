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
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.TimeIt;

import java.util.Arrays;

import static org.maxicp.modeling.Factory.*;

/**
 * Traveling Salesman Problem with Time Windows (TSPTW) using a successor-based routing model.
 *
 * <p>Model:
 * <ul>
 *   <li>{@code succ[i]} = successor of node i in the tour</li>
 *   <li>{@link Factory#circuit} on succ</li>
 *   <li>{@code arrive[i]} = arrival time at node i, propagated forward: {@code arrive[succ[i]] >= arrive[i] + dist[i][succ[i]]}</li>
 * </ul>
 *
 * <p>Instances from: https://lopez-ibanez.eu/tsptw-instances
 */
public class TSPTWSuccessor {

    public static void main(String[] args) {

        TSPTWInstance instance = new TSPTWInstance("data/TSPTW/Dumas/n40w20.001.txt");
        int n = instance.n;
        int[][] dist = instance.distMatrix;
        int[] earliest = instance.earliest;
        int[] latest = instance.latest;
        int horizon = instance.horizon;

        ModelDispatcher model = makeModelDispatcher();

        // --- Successor variables ---
        IntVar[] succ = model.intVarArray(n, n);

        // Circuit on successors
        model.add(circuit(succ));

        // --- Arrival time variables ---
        IntExpression[] arrive = model.intVarArray(n, i -> model.intVar(0, horizon));

        // Depot starts at time 0, circuit closes back to depot
        model.add(eq(arrive[0], 0));
        model.add(eq(succ[n - 1], 0));

        // --- Time window constraints ---
        for (int i = 0; i < n; i++) {
            model.add(le(arrive[i], latest[i]));
            model.add(ge(arrive[i], earliest[i]));
        }

        // --- Cost and time propagation ---
        // distSucc[i] = dist[i][succ[i]] — used for both cost and time propagation
        IntExpression[] distSucc = new IntExpression[n];
        for (int i = 0; i < n; i++) {
            distSucc[i] = get(dist[i], succ[i]);
        }
        IntExpression totalDist = sum(distSucc);

        for (int i = 0; i < n; i++) {
            if (i == n - 1) continue; // skip arc from depot-duplicate back to depot
            // arrive[succ[i]] >= arrive[i] + distSucc[i]
            IntExpression arriveSucc = get(arrive, succ[i]);
            model.add(le(sum(arrive[i], distSucc[i]), arriveSucc));
        }

        long time = TimeIt.run(() -> model.runCP((cp) -> {
            DFSearch search = cp.dfSearch(Searches.fds(succ));
            search.onSolution(() -> {
                int[] tour = new int[n];
                int cur = 0;
                for (int i = 0; i < n; i++) {
                    tour[i] = cur;
                    cur = succ[cur].min();
                }
                System.out.println("tour: " + Arrays.toString(tour));
                System.out.println("total distance: " + totalDist);
                assert instance.checkSolution(tour, totalDist.min());
            });
            SearchStatistics stats = search.optimize(minimize(totalDist));
            System.out.println(stats);
        }));

        System.out.println("Time (s): " + (time / 1_000_000_000.));
    }
}
