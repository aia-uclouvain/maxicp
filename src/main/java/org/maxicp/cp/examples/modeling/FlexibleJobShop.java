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

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;
import static org.maxicp.search.Searches.and;
import static org.maxicp.search.Searches.branch;

public class FlexibleJobShop {

    /**
     * Example taken from
     * {@literal  Chen, H., Ihlow, J., & Lehmann, C. (1999, May). A genetic algorithm for flexible job-shop scheduling. In Proceedings 1999 IEEE International Conference on Robotics and Automation (Cat. No. 99CH36288C) (Vol. 2, pp. 1120-1125). Ieee.}
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

        ModelDispatcher baseModel = Factory.makeModelDispatcher();
        List<IntervalVar>[] taskOnMachine = new List[nMachines];
        for (int i = 0; i < nMachines; i++) {
            taskOnMachine[i] = new ArrayList<>();
        }
        IntervalVar[] tasks = new IntervalVar[nTasks];
        List<IntervalVar>[] taskCandidates = new List[nTasks];
        // create the tasks
        for (int task = 0 ; task < nTasks ; task++) {
            taskCandidates[task] = new ArrayList<>();
            for (int machine = 0 ; machine < nMachines ; machine++) {
                if (duration[task][machine] != -1) {
                    IntervalVar possibleOperation = baseModel.intervalVar(duration[task][machine]);
                    taskOnMachine[machine].add(possibleOperation);
                    taskCandidates[task].add(possibleOperation);
                }
            }
            // effective real task, which must be present
            tasks[task] = baseModel.intervalVar(true);
            // effective real task is one alternative among the ones that are present
            baseModel.add(Factory.alternative(tasks[task], taskCandidates[task].toArray(IntervalVar[]::new)));
        }
        // no overlap on any machine
        for (int machine = 0 ; machine < nMachines ; machine++) {
            baseModel.add(noOverlap(taskOnMachine[machine].toArray(IntervalVar[]::new)));
        }
        // precedences within tasks in a job
        for (int job = 0 ; job < nJobs ; job++) {
            for (int task = 0 ; task < jobs[job].length - 1 ; task++) {
                int taskA = jobs[job][task];
                int taskB = jobs[job][task + 1];
                baseModel.add(Factory.endBeforeStart(tasks[taskA], tasks[taskB]));
            }
        }
        IntExpression makespan = max(Arrays.stream(jobs).map(op -> tasks[op[op.length - 1]]).map(task -> endOr(task, 0)).toArray(IntExpression[]::new));
        Objective minimizeMakespan = minimize(makespan);

        // first step: assign the presence of the intervals to the machines
        IntExpression[] allPresences = Arrays.stream(taskCandidates).flatMap(list -> list.stream())
                .map(IntervalVar::status).toArray(IntExpression[]::new);
        baseModel.runCP((cp) -> {
            Supplier<Runnable[]> assignToMachine = firstFail(allPresences);
            // second step: once the present intervals are chosen, fix the time
            Supplier<Runnable[]> setTimes = setTimes(tasks);
            // third step: fix the makespan once the times are fixed
            Supplier<Runnable[]> fixMakespan = () -> {
                if (makespan.isFixed())
                    return EMPTY;
                return branch(() -> makespan.getModelProxy().add(eq(makespan,makespan.min())));
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
