/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

import org.maxicp.cp.engine.constraints.scheduling.*;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;

import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.io.InputReader;

import java.util.function.Supplier;

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
            for (int r = 0; r < nResources; r++) {
                consumption[r][i] = reader.getInt();
            }
            successors[i] = new int[reader.getInt()];
            for (int k = 0; k < successors[i].length; k++) {
                successors[i][k] = reader.getInt() - 1;
            }
        }

        // -------------------------------------------

        // The Model

        CPSolver cp = makeSolver();

        CPIntervalVar[] tasks = makeIntervalVarArray(cp, nActivities);

        for (int i = 0; i < nActivities; i++) {
            tasks[i].setLength(duration[i]);
            tasks[i].setPresent();
        }

        CPCumulFunction[] resources = new CPCumulFunction[nResources];

        for (int r = 0; r < nResources; r++) {
            resources[r] = new CPFlatCumulFunction();
        }
        for (int i = 0; i < nActivities; i++) {
            for (int r = 0; r < nResources; r++) {
                if (consumption[r][i] > 0) {
                    resources[r] = new CPPlusCumulFunction(resources[r], new CPPulseCumulFunction(tasks[i], consumption[r][i]));
                }
            }
        }

        for (int r = 0; r < nResources; r++) {
            cp.post(le(resources[r], capa[r]));
        }

        for (int i = 0; i < nActivities; i++) {
            for (int k : successors[i]) {
                // activity i must precede activity k
                cp.post(new EndBeforeStart(tasks[i], tasks[k]));
            }
        }

        CPIntVar makespan = makespan(tasks);

        Objective obj = cp.minimize(makespan);

        Supplier<Runnable[]> fixMakespan = () -> makespan.isFixed() ? EMPTY : new Runnable[]{() -> {
            cp.post(CPFactory.eq(makespan, makespan.min()));
        }};

        DFSearch dfs = CPFactory.makeDfs(cp, and(setTimes(tasks, i -> i), fixMakespan));

        dfs.onSolution(() -> {
            System.out.println("makespan:" + makespan);
        });

        SearchStatistics stats = dfs.optimize(obj);

        System.out.format("Statistics: %s\n", stats);
    }
}