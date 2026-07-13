/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling.tsptw;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.examples.utils.TSPTWInstance;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.TimeIt;

import static org.maxicp.modeling.Factory.*;

/**
 * Traveling Salesman Problem with Time Windows (TSPTW) using a scheduling model
 * with interval variables and a noOverlap constraint with position variables.
 *
 * <p>Model:
 * <ul>
 *   <li>Each visit is an interval variable of length 0 with time windows as start/end bounds</li>
 *   <li>{@link Factory#noOverlap} with position variables and transition times</li>
 *   <li>Position variables channeled via the noOverlap constraint</li>
 *   <li>Transition cost computed with element constraints on the position variables</li>
 * </ul>
 *
 * <p>Instances from: https://lopez-ibanez.eu/tsptw-instances
 */
public class TSPTWScheduling {

    public static void main(String[] args) {

        TSPTWInstance instance = new TSPTWInstance("data/TSPTW/Dumas/n40w20.001.txt");
        int n = instance.n;
        int[][] dist = instance.distMatrix;

        ModelDispatcher model = Factory.makeModelDispatcher();

        // Visits as interval variables of length 0 with time windows
        IntervalVar[] visits = new IntervalVar[n];
        for (int i = 0; i < n; i++) {
            visits[i] = model.intervalVar(instance.earliest[i], instance.latest[i], 0, true);
        }

        // Position variables
        IntVar[] posOfVisits = model.intVarArray(n, n);
        IntVar[] visitsInPos = model.intVarArray(n, n);

        // NoOverlap with position variables and transition times
        model.add(noOverlap(visits, posOfVisits, visitsInPos, dist));

        // Depot constraints
        model.add(eq(posOfVisits[0], 0));             // start at depot
        model.add(start(visits[0], 0));               // at time 0
        model.add(eq(posOfVisits[n - 1], n - 1));     // end at depot-duplicate

        // Transition cost: sum of dist[visitsInPos[i]][visitsInPos[i+1]]
        IntExpression[] transitionTimes = new IntExpression[n - 1];
        for (int i = 0; i < n - 1; i++) {
            transitionTimes[i] = get(dist, visitsInPos[i], visitsInPos[i + 1]);
        }
        IntExpression totalDist = sum(transitionTimes);

        long t = TimeIt.run(() -> model.runCP((cp) -> {
            DFSearch dfs = cp.dfSearch(Searches.heuristicNary(
                    Searches.staticOrderVariableSelector(visitsInPos),
                    i -> posOfVisits[i].size()));

            dfs.onSolution(() -> {
                int[] tour = new int[n];
                for (int i = 0; i < n; i++) {
                    tour[i] = visitsInPos[i].min();
                }
                System.out.println("tour: " + java.util.Arrays.toString(tour));
                System.out.println("total distance: " + totalDist);
                assert instance.checkSolution(tour, totalDist.min());
            });

            SearchStatistics stats = dfs.optimize(minimize(totalDist));
            System.out.println(stats);
        }));

        System.out.println("Time (s): " + (t / 1_000_000_000.));
    }
}
