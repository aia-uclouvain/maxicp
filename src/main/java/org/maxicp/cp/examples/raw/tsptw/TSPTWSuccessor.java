/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.tsptw;

import org.maxicp.cp.engine.constraints.CostAllDifferentDC;
import org.maxicp.cp.engine.constraints.InversePerm;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.examples.utils.TSPTWInstance;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

/**
 * Traveling Salesman Problem with Time Windows (TSPTW) using a successor-based routing model.
 *
 * <p>Model:
 * <ul>
 *   <li>{@code succ[i]} = successor of node i in the tour</li>
 *   <li>{@link org.maxicp.cp.engine.constraints.Circuit} on succ</li>
 *   <li>{@code arrive[i]} = arrival time at node i, propagated forward via successor: {@code arrive[succ[i]] >= arrive[i] + dist[i][succ[i]]}</li>
 *   <li>{@link CostAllDifferentDC} as a redundant filtering for the total distance</li>
 * </ul>
 *
 * <p>The depot is duplicated at the end of the instance (node n-1).
 * The tour starts at node 0 and ends at node n-1, so {@code succ[n-1] = 0} closes the circuit.
 * The arrival time at node 0 is fixed to 0.
 *
 * <p>Time window constraints: for each node i,
 * {@code earliest[i] <= arrive[i] <= latest[i]} and
 * {@code arrive[succ[i]] >= arrive[i] + dist[i][succ[i]]}.
 * The latter uses the row {@code dist[i]} directly via an element constraint,
 * avoiding the need to extract columns from the distance matrix.
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

        CPSolver cp = makeSolver();

        // --- Successor variables ---

        CPIntVar[] succ = makeIntVarArray(cp, n, n);

        // Circuit on successors
        cp.post(circuit(succ));

        // --- Arrival time variables ---

        CPIntVar[] arrive = makeIntVarArray(cp, n, 0, horizon);

        // Depot starts at time 0
        cp.post(eq(arrive[0], 0));
        // Depot duplicate (node n-1) has the same time window as depot
        cp.post(eq(succ[n - 1], 0)); // close the circuit back to depot

        // --- Time window constraints ---

        for (int i = 0; i < n; i++) {
            // earliest[i] <= arrive[i] <= latest[i]
            cp.post(le(arrive[i], latest[i]));
            cp.post(ge(arrive[i], earliest[i]));
        }

        // --- Cost and time propagation via element constraints ---
        // distSucc[i] = dist[i][succ[i]]
        //   arrive[succ[i]] >= arrive[i] + dist[i][succ[i]]

        CPIntVar[] distSucc = new CPIntVar[n];
        for (int i = 0; i < n; i++) {
            distSucc[i] = element(dist[i], succ[i]);
        }
        CPIntVar totalDist = sum(distSucc);

        for (int i = 0; i < n; i++) {
            if (i == n - 1) continue; // skip the arc from depot-duplicate back to depot

            // arrive[succ[i]] >= arrive[i] + distSucc[i]
            CPIntVar arriveSucc = element(arrive, succ[i]);
            CPIntVar departPlusTravel = sum(arrive[i], distSucc[i]);
            cp.post(le(departPlusTravel, arriveSucc));
        }

        // Redundant cost-based filtering
        cp.post(new CostAllDifferentDC(succ, dist, totalDist));

        // --- Search ---

        DFSearch dfs = makeDfs(cp, fds(succ));

        dfs.onSolution(() -> {
            // Reconstruct the tour
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

        Objective obj = cp.minimize(totalDist);
        long t0 = System.currentTimeMillis();
        SearchStatistics stats = dfs.optimize(obj);
        System.out.println(stats);
        System.out.println("Time: " + (System.currentTimeMillis() - t0) + "ms");
    }
}
