package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;
import static org.maxicp.search.Searches.and;
import static org.maxicp.search.Searches.branch;

public class FlexibleJobShop {

    /**
     * Example taken from
     * {@literal  Chen, H., Ihlow, J., & Lehmann, C. (1999, May).
     * A genetic algorithm for flexible job-shop scheduling.
     * In Proceedings 1999 IEEE International Conference on Robotics and Automation (Cat. No. 99CH36288C) (Vol. 2, pp. 1120-1125). Ieee.}
     */
    public static void main(String[] args) {
        int nJobs = 2;
        int nMachines = 3;
        // for each job, id's of the corresponding tasks
        int[][] jobs = new int[][] {
                {0, 1, 2},
                {3, 4, 5},
        };
        int nTasks = Arrays.stream(jobs).map(operation -> operation.length).reduce(0, Integer::sum);
        // duration[task][machine] == processing time for a task on the given machine
        // -1 means that the task cannot be processed on this machine
        int[][] duration = new int[][] {
                {1, 2, 1},
                {-1, 1, 1},
                {4, 3, -1},
                {5, -1, 2},
                {-1, 2, -1},
                {7, 5, 3},
        };

        ModelDispatcher model = makeModelDispatcher();
        List<IntervalVar>[] tasksOnMachine = new List[nMachines];
        for (int m = 0; m < nMachines; m++) {
            tasksOnMachine[m] = new ArrayList<>();
        }

        IntervalVar[] tasks = new IntervalVar[nTasks]; // the effective tasks
        List<IntervalVar>[] alternativeTasks = new List[nTasks]; // possible alternative tasks for each task
        List<IntExpression> status = new ArrayList<>(); // presence of the optional tasks
        // create the tasks
        for (int t = 0 ; t < nTasks ; t++) {
            alternativeTasks[t] = new ArrayList<>();
            for (int m = 0 ; m < nMachines ; m++) {
                if (duration[t][m] != -1) {
                    IntervalVar possibleOperation = model.intervalVar(duration[t][m],false);
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
        for (int m = 0 ; m < nMachines ; m++) {
            model.add(noOverlap(tasksOnMachine[m].toArray(IntervalVar[]::new)));
        }

        List<IntExpression> jobEndTimes = new ArrayList<>();
        // precedences within tasks in a job
        for (int j = 0 ; j < nJobs ; j++) {
            for (int t = 0 ; t < jobs[j].length - 1 ; t++) {
                int taskA = jobs[j][t];
                int taskB = jobs[j][t + 1];
                model.add(endBeforeStart(tasks[taskA], tasks[taskB]));
                if (t == jobs[j].length - 2) {
                    jobEndTimes.add(endOr(tasks[taskB], 0)); // end of the last task of the job
                }
            }
        }

        IntExpression makespan = max(jobEndTimes.toArray(IntExpression[]::new));
        Objective minimizeMakespan = minimize(makespan);

        model.runCP((cp) -> {
            // first step: assign the presence of the intervals to the machines
            Supplier<Runnable[]> assignToMachine = staticOrder(status.toArray(IntExpression[]::new));
            // second step: once the present intervals are chosen, fix the time
            Supplier<Runnable[]> setTimes = setTimes(tasks);
            // third step: fix the makespan once the times are fixed
            Supplier<Runnable[]> fixMakespan = () -> {
                if (makespan.isFixed())
                    return EMPTY;
                return branch(() -> model.add(eq(makespan,makespan.min())));
            };
            DFSearch search = cp.dfSearch(and(assignToMachine, setTimes, fixMakespan));
            // print each solution found
            search.onSolution(() -> {
                System.out.println("makespan: " + makespan);
            });
            SearchStatistics stats = search.optimize(minimizeMakespan); // actually solve the problem
            System.out.println("stats: \n" + stats);
        });
    }

}
