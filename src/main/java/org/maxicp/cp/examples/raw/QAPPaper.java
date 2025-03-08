/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.util.io.InputReader;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.EMPTY;

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
public class QAPPaper {
    public static void main(String[] args) {
        // ---- read the instance -----
        InputReader reader = new InputReader("data/qap.txt");

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
        // Model creation and resolution
        CPSolver cp = makeSolver();
        CPIntVar[] x = makeIntVarArray(cp, n, n);

        cp.post(allDifferent(x));
        CPIntVar[] weightedDist = new CPIntVar[n * n];
        int k = 0;
        for (int i = 0; i < n; i++) 
            for (int j = 0; j < n; j++) {
                CPIntVar dij = element(d, x[i], x[j]);
                weightedDist[k++] = mul(dij,w[i][j]);
            }
        CPIntVar totCost  = sum(weightedDist);
        Objective obj = cp.minimize(totCost);

        DFSearch dfs = makeDfs(cp,() -> {
                int idx = -1; // index of the first variable that is not fixed
                for (int l = 0; l < x.length; l++)
                    if (x[l].size() > 1) {
                        idx = l;
                        break;
                    }
                if (idx == -1)
                    return EMPTY;
                else {
                    CPIntVar xi = x[idx];
                    int v = xi.min();
                    Runnable left = () -> cp.post(CPFactory.eq(xi, v));
                    Runnable right = () -> cp.post(CPFactory.neq(xi, v));
                    return new Runnable[]{left, right};
                }
            }
            );

        dfs.onSolution(() -> {
                System.out.println("objective:" + totCost.min());
        });
        dfs.optimize(obj);
    }
}
