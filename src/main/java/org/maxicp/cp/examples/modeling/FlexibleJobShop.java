package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.examples.utils.FJSInstance;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.maxicp.modeling.Factory.*;
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
        String instance = args.length > 0 ? args[0] : "data/FJOBSHOP/brandimarte/mk01.txt";
        FJSInstance fjs = new FJSInstance(instance);
        int nJobs = fjs.nJobs;
        int nMachines = fjs.nMachines;
        int[][] jobs = fjs.jobs;
        int nTasks = fjs.nTasks;
        int[][] duration = fjs.duration;

        ModelDispatcher model = makeModelDispatcher();
        List<IntervalVar>[] tasksOnMachine = new List[nMachines];
        for (int m = 0; m < nMachines; m++) {
            tasksOnMachine[m] = new ArrayList<>();
        }

        IntervalVar[] tasks = new IntervalVar[nTasks]; // the effective tasks
        List<IntervalVar>[] alternativeTasks = new List[nTasks]; // possible alternative tasks for each task
        List<IntExpression> status = new ArrayList<>(); // presence of the optional tasks
        // create the tasks
        for (int t = 0; t < nTasks; t++) {
            alternativeTasks[t] = new ArrayList<>();
            for (int m = 0; m < nMachines; m++) {
                if (duration[t][m] != -1) {
                    IntervalVar possibleOperation = model.intervalVar(duration[t][m], false);
                    tasksOnMachine[m].add(possibleOperation);
                    alternativeTasks[t].add(possibleOperation);
                    status.add(possibleOperation.status());
                }
            }
            // effective real t, which must be present
            tasks[t] = model.intervalVar(true);
            // effective real t is one alternative among the ones that are present
            model.add(alternative(tasks[t], alternativeTasks[t].toArray(IntervalVar[]::new)));
        }
        // no overlap on any machine
        for (int m = 0; m < nMachines; m++) {
            model.add(noOverlap(tasksOnMachine[m].toArray(IntervalVar[]::new)));
        }

        List<IntExpression> jobEndTimes = new ArrayList<>();
        // precedences within tasks in a job
        for (int j = 0; j < nJobs; j++) {
            if (jobs[j].length == 1) {
                jobEndTimes.add(endOr(tasks[jobs[j][0]], 0));
            } else {
                for (int t = 0; t < jobs[j].length - 1; t++) {
                    int taskA = jobs[j][t];
                    int taskB = jobs[j][t + 1];
                    model.add(endBeforeStart(tasks[taskA], tasks[taskB]));
                    if (t == jobs[j].length - 2) {
                        jobEndTimes.add(endOr(tasks[taskB], 0)); // end of the last task of the job
                    }
                }
            }
        }

        IntExpression makespan = max(jobEndTimes.toArray(IntExpression[]::new));
        Objective minimizeMakespan = minimize(makespan);

        model.runCP((cp) -> {
            List<IntervalVar> allIntervals = new ArrayList<>();
            allIntervals.addAll(Arrays.asList(tasks));
            for (int m = 0; m < nMachines; m++) {
                allIntervals.addAll(tasksOnMachine[m]);
            }
            DFSearch search = cp.dfSearch(fds(allIntervals.toArray(IntervalVar[]::new)));
            // print each solution found
            search.onSolution(() -> {
                System.out.println("makespan: " + makespan);
            });
            SearchStatistics stats = search.optimize(minimizeMakespan); // actually solve the problem
            System.out.println("stats: \n" + stats);
        });
    }

}
