/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;
import java.util.function.Supplier;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

/**
 * Resource Constrained Project Scheduling Problem.
 * <a href="http://www.om-db.wi.tum.de/psplib/library.html">PSPLIB</a>.
 */
public class RCPSP {

    public static void main(String[] args) {
        // Reading the data
        InputReader reader = new InputReader("data/RCPSP/j30_1_1.rcp");

        int nActivities = reader.getInt();
        int nResources = reader.getInt();

        int[] capa = new int[nResources];
        for (int i = 0; i < nResources; i++) {
            capa[i] = reader.getInt();
        }

        int[] duration = new int[nActivities];
        int[][] consumption = new int[nResources][nActivities];
        int[][] successors = new int[nActivities][];

        for (int i = 0; i < nActivities; i++) {
            // durations, demand for each resource, successors
            duration[i] = reader.getInt();
            for (int r = 0; r < nResources; r++)
                consumption[r][i] = reader.getInt();
            successors[i] = new int[reader.getInt()];
            for (int k = 0; k < successors[i].length; k++)
                successors[i][k] = reader.getInt() - 1;
        }

        // -------------------------------------------

        // The Model
        ModelDispatcher baseModel = Factory.makeModelDispatcher();
        IntervalVar[] tasks = baseModel.intervalVarArray(nActivities);
        for (int i = 0; i < nActivities; i++) {
            baseModel.add(length(tasks[i], duration[i]));
            baseModel.add(present(tasks[i]));
        }

        CumulFunction[] resources = new CumulFunction[nResources];
        for (int r = 0; r < nResources; r++) {
            resources[r] = flat();
        }
        for (int i = 0; i < nActivities; i++) {
            for (int r = 0; r < nResources; r++) {
                if (consumption[r][i] > 0) {
                    resources[r] = sum(resources[r], pulse(tasks[i], consumption[r][i]));
                }
            }
        }

        for (int r = 0; r < nResources; r++) {
            baseModel.add(lessOrEqual(resources[r], capa[r]));
        }

        for (int i = 0; i < nActivities; i++) {
            for (int k : successors[i]) {
                // activity i must precede activity k
                baseModel.add(endBeforeStart(tasks[i], tasks[k]));
            }
        }

        IntExpression makespan = max(Arrays.stream(tasks).map(task -> endOr(task, 0)).toArray(IntExpression[]::new));
        Objective obj = minimize(makespan);

        baseModel.runCP((cp) -> {

            Supplier<Runnable[]> fixMakespan = () -> {
                if (makespan.isFixed())
                    return EMPTY;
                return branch(() -> baseModel.add(eq(makespan, makespan.min())));
            };

            Supplier<Runnable[]> branching = and(setTimes(tasks), fixMakespan);

            DFSearch search = cp.dfSearch(branching);
            // print each solution found
            search.onSolution(() -> {
                System.out.println("makespan: " + makespan);
            });
            SearchStatistics stats = search.optimize(obj); // actually solve the problem
            System.out.println("stats: \n" + stats);
        });

    }

}
