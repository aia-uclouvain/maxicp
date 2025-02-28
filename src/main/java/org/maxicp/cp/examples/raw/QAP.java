/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.io.InputReader;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.firstFail;

/**
 * The Quadratic Assignment problem.
 * There are a set of n facilities and a set of n locations.
 * For each pair of locations, a distance is specified and for
 * each pair of facilities a weight or flow is specified
 * (e.g., the amount of supplies transported between the two facilities).
 * The problem is to assign all facilities to different locations
 * with the goal of minimizing the sum of the distances multiplied
 * by the corresponding flows.
 * <a href="https://en.wikipedia.org/wiki/Quadratic_assignment_problem">Wikipedia</a>.
 */
public class QAP {

    public static void main(String[] args) {

        // ---- read the instance -----

        InputReader reader = new InputReader("data/QAP/qap.txt");

        int n = reader.getInt();
        // Weights
        int[][] w = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                w[i][j] = reader.getInt();
            }
        }
        // Distance
        int[][] d = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                d[i][j] = reader.getInt();
            }
        }

        // ----- build the model ---
        solve(n, w, d, true, stats -> false);
    }

    /**
     * @param n       size of the problem
     * @param w       weights
     * @param d       distances
     * @param verbose indicates if the solver should indicates on stdout its progression
     * @param limit   allow to interrupt the solver faster if needed. See dfs.solve().
     * @return list of solutions encountered
     */
    public static List<Integer> solve(int n, int[][] w, int[][] d, boolean verbose, Predicate<SearchStatistics> limit) {
        CPSolver cp = makeSolver();
        CPIntVar[] x = makeIntVarArray(cp, n, n);

        cp.post(allDifferent(x));


        // build the objective function
        CPIntVar[] weightedDist = new CPIntVar[n * n];
        for (int k = 0, i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                weightedDist[k] = mul(element(d, x[i], x[j]), w[i][j]);
                k++;
            }
        }
        CPIntVar totCost = sum(weightedDist);
        Objective obj = cp.minimize(totCost);

        /*
        // discrepancy search
        for (int dL = 0; dL < x.length; dL++) {
            DFSearch dfs = makeDfs(cp, limitedDiscrepancy(firstFail(x), dL));
            dfs.optimize(obj);
        }
        */

        DFSearch dfs = makeDfs(cp, firstFail(x));

        ArrayList<Integer> solutions = new ArrayList<>();
        dfs.onSolution(() -> {
            solutions.add(totCost.min());

            if (verbose)
                System.out.println("objective:" + totCost.min());
        });

        SearchStatistics stats = dfs.optimize(obj, limit);
        if (verbose)
            System.out.println(stats);

        return solutions;
    }
}
