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
 * Traveling Salesman Problem with Time Windows (TSPTW) using a position-based routing model.
 *
 * <p>Model:
 * <ul>
 *   <li>{@code x[i]} = node visited at position i in the tour</li>
 *   <li>{@code arrival[i]} = arrival time at position i</li>
 *   <li>{@link Factory#allDifferent} on x</li>
 *   <li>Time windows enforced via element constraints on earliest/latest arrays</li>
 *   <li>Transition times via element constraints on the distance matrix</li>
 * </ul>
 *
 * <p>Instances from: https://lopez-ibanez.eu/tsptw-instances
 */
public class TSPTW {

    public static void main(String[] args) {

        TSPTWInstance instance = new TSPTWInstance("data/TSPTW/Dumas/n40w20.001.txt");
        int n = instance.n;
        int[][] dist = instance.distMatrix;
        int[] earliest = instance.earliest;
        int[] latest = instance.latest;
        int horizon = instance.horizon;

        ModelDispatcher model = Factory.makeModelDispatcher();

        // x[i] = node visited at position i
        IntVar[] x = model.intVarArray(n, n);

        // arrival[i] = arrival time at position i
        IntVar[] arrival = model.intVarArray(n, horizon);

        model.add(allDifferent(x));

        // Depot: start at node 0 at time 0
        model.add(eq(arrival[0], 0));
        model.add(eq(x[0], 0));

        // Last position is the depot duplicate
        model.add(eq(x[n - 1], n - 1));

        // Time windows and transition times
        IntExpression[] transition = new IntExpression[n - 1];
        for (int i = 0; i < n; i++) {
            IntExpression earliestAtPos = get(earliest, x[i]);
            IntExpression latestAtPos = get(latest, x[i]);
            model.add(le(arrival[i], latestAtPos));
            model.add(le(earliestAtPos, arrival[i]));
        }

        for (int i = 0; i < n - 1; i++) {
            transition[i] = get(dist, x[i], x[i + 1]);
            IntExpression arrivalPlusTransition = sum(arrival[i], transition[i]);
            model.add(le(arrivalPlusTransition, arrival[i + 1]));
        }

        IntExpression totalDist = sum(transition);

        long time = TimeIt.run(() -> model.runCP((cp) -> {
            DFSearch search = cp.dfSearch(Searches.fds(x));

            search.onSolution(() -> {
                int[] tour = new int[n];
                for (int i = 0; i < n; i++) {
                    tour[i] = x[i].min();
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
