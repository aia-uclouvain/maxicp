/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchMethod;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static org.maxicp.modeling.Factory.*;

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
        System.out.println(run(
                (baseModel, x) -> baseModel.dfSearch(Searches.fds(x))));
    }

    public static SearchStatistics run(BiFunction<ModelDispatcher, IntExpression[], SearchMethod> search) {

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
        ModelDispatcher baseModel = Factory.makeModelDispatcher();

        IntVar[] x = baseModel.intVarArray(n, n);

        baseModel.add(Factory.allDifferent(x));

        // build the objective function
        IntExpression[] weightedDist = new IntExpression[n * n];
        int ind = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                weightedDist[ind] = mul(get(d, x[i], x[j]), w[i][j]);
                ind++;
            }
        }
        IntExpression totCost = sum(weightedDist);
        Objective minimizeDistance = minimize(totCost, true);


        return baseModel.runCP(cp -> {

            // Find a good solution with a few LNS iterations

            System.out.println("lns ...");

            int[] xBest = IntStream.range(0, n).toArray();
            AtomicInteger bestCost = new AtomicInteger(Integer.MAX_VALUE - 1);

            int nRestarts = 100;
            int failureLimit = 100;
            Random rand = new java.util.Random(0);


            DFSearch dfs = baseModel.dfSearch(Searches.firstFailBinary(x));

            dfs.onSolution(() -> {
                System.out.println("new solution with objective:" + totCost.min() + " " + totCost.max());
                // Update the current best solution
                for (int i = 0; i < n; i++) {
                    xBest[i] = x[i].min();
                }
                bestCost.set(totCost.min());
            });

            for (int i = 0; i < nRestarts; i++) {

                if (i % 10 == 0)
                    System.out.println("restart number #" + i);

                dfs.optimizeSubjectTo(minimizeDistance, statistics -> statistics.numberOfFailures() >= failureLimit, () -> {
                    // Assign the fragment 5% of the variables randomly chosen
                    for (int j = 0; j < n; j++) {
                        if (rand.nextInt(100) < 50) {
                            // after the solveSubjectTo those constraints are removed
                            baseModel.add(eq(x[j], xBest[j]));
                        }
                    }
                });
            }

            System.out.println("now try to prove optimality");


            // Now prove optimality without LNS
            return dfs.optimize(minimizeDistance); // actually solve the problem


        });

    }
}
