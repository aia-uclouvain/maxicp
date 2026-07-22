/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.examples.utils.FJSInstance;
import org.maxicp.search.DFSearch;
import org.maxicp.search.FDSModeling;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

/**
 * The Flexible Job Shop Problem (FJSP) is an extension of the classical Job
 * Shop Scheduling Problem.
 * It consists of a set of jobs, each comprising a sequence of operations that
 * must be executed in a given order.
 * Unlike the classical job shop where each operation is assigned to a specific
 * machine, in the FJSP, each operation
 * can be processed on any machine from a given set of available machines, with
 * potentially different processing times.
 * The goal is to assign each operation to a machine and schedule their start
 * times such that the maximum completion time (makespan) is minimized.
 * 
 * @author Pierre Schaus
 */
public class FlexibleJobShop {

    public static void main(String[] args) {
        String instancePath = args.length > 0 ? args[0] : "data/FJOBSHOP/brandimarte/mk01.txt";
        FJSInstance fjs = new FJSInstance(instancePath);

        int nJobs = fjs.nJobs;
        int nMachines = fjs.nMachines;
        int[][] jobs = fjs.jobs;
        int nTasks = fjs.nTasks;
        int[][] duration = fjs.duration;

        CPSolver cp = CPFactory.makeSolver();

        List<CPIntervalVar>[] tasksOnMachine = new List[nMachines];
        for (int m = 0; m < nMachines; m++) {
            tasksOnMachine[m] = new ArrayList<>();
        }

        CPIntervalVar[] tasks = new CPIntervalVar[nTasks]; // the effective tasks
        List<CPIntervalVar>[] alternativeTasks = new List[nTasks]; // possible alternative tasks for each task

        // create the tasks
        for (int t = 0; t < nTasks; t++) {
            alternativeTasks[t] = new ArrayList<>();
            
            // effective real t, which must be present
            tasks[t] = makeIntervalVar(cp);
            tasks[t].setPresent();

            for (int m = 0; m < nMachines; m++) {
                if (duration[t][m] != -1) {
                    CPIntervalVar possibleOperation = makeIntervalVar(cp, true, duration[t][m]);
                    tasksOnMachine[m].add(possibleOperation);
                    alternativeTasks[t].add(possibleOperation);
                }
            }
            
            // effective real t is one alternative among the ones that are present
            cp.post(alternative(tasks[t], alternativeTasks[t].toArray(new CPIntervalVar[0])));
        }

        // no overlap on any machine
        for (int m = 0; m < nMachines; m++) {
            cp.post(noOverlap(tasksOnMachine[m].toArray(new CPIntervalVar[0])));
        }

        CPIntervalVar[] lasts = new CPIntervalVar[nJobs];

        // precedences within tasks in a job
        for (int j = 0; j < nJobs; j++) {
            for (int t = 0; t < jobs[j].length - 1; t++) {
                int taskA = jobs[j][t];
                int taskB = jobs[j][t + 1];
                cp.post(endBeforeStart(tasks[taskA], tasks[taskB]));
            }
            lasts[j] = tasks[jobs[j][jobs[j].length - 1]];
        }

        CPIntVar makespan = makespan(lasts);
        Objective obj = cp.minimize(makespan);

        List<CPIntervalVar> allIntervals = new ArrayList<>();
        allIntervals.addAll(Arrays.asList(tasks));
        for (int m = 0; m < nMachines; m++) {
            allIntervals.addAll(tasksOnMachine[m]);
        }

        DFSearch dfs = CPFactory.makeDfs(cp, new FDSModeling(allIntervals.toArray(new CPIntervalVar[0])));

        dfs.onSolution(s -> {
            System.out.println("makespan: " + makespan.min());
        });

        SearchStatistics stats = dfs.optimize(obj);
        System.out.println("stats: \n" + stats);
    }
}
