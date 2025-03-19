/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.bool.BoolExpression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.io.InputReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

public class JobShop {

    public static void main(String[] args) {
        InputReader inputReader = new InputReader("data/JOBSHOP/jobshop-8-8-0");
        for (int i = 0 ; i < 4 ; i++)
            inputReader.skipLine(); // ignore first lines
        int nJobs = inputReader.getInt();
        int nMachines = inputReader.getInt();
        int[][] duration = new int[nJobs][nMachines];
        int[][] machine = new int[nJobs][nMachines];

        for (int i = 0; i < nJobs; i++) {
            for (int j = 0; j < nMachines; j++) {
                machine[i][j] = inputReader.getInt();
                duration[i][j] = inputReader.getInt();
            }
        }

        ModelDispatcher model = makeModelDispatcher();
        IntervalVar[][] activities = new IntervalVar[nJobs][nMachines];
        ArrayList<IntervalVar>[] activitiesOnMachine = new ArrayList[nMachines];
        for (int i = 0 ; i < activitiesOnMachine.length ; i++)
            activitiesOnMachine[i] = new ArrayList<>();
        IntervalVar[] lastActivityOfJob = new IntervalVar[nJobs];

        for (int i = 0; i < nJobs; i++) {
            for (int j = 0; j < nMachines; j++) {
                // each activity has a fixed duration and is always present
                activities[i][j] = model.intervalVar(duration[i][j], true);
                int m = machine[i][j];
                activitiesOnMachine[m].add(activities[i][j]);
                // task comes before the other one on the same machine
                if (j > 0)
                    model.add(endBeforeStart(activities[i][j - 1], activities[i][j]));
            }
            lastActivityOfJob[i] = activities[i][nMachines - 1];
        }
        // collect the precedences between tasks
        List<BoolExpression> precedences = new ArrayList<>();
        for (int m = 0; m < nMachines ; m++) {
            // no task can overlap on a machine
            IntervalVar[] act = activitiesOnMachine[m].toArray(new IntervalVar[0]);
            model.add(noOverlap(act));
            // add the precedence
            for (int i = 0; i < act.length; i++) {
                for (int j = i + 1; j < act.length; j++) {
                    BoolExpression iBeforeJ = endBeforeStart(act[i], act[j]);
                    BoolExpression jBeforeI = endBeforeStart(act[j], act[i]);
                    // the tasks cannot overlap: either i << j, or j << i
                    model.add(neq(iBeforeJ, jBeforeI));
                    // register the precedence to branch on it
                    precedences.add(iBeforeJ);
                }
            }
        }
        IntExpression makespan = max(Arrays.stream(lastActivityOfJob).map(task -> endOr(task, 0)).toArray(IntExpression[]::new));
        Objective minimizeMakespan = minimize(makespan);
        IntExpression[] precedencesArray = precedences.toArray(new IntExpression[0]);

        Supplier<Runnable[]> branchPrecedences = firstFail(precedencesArray);
        Supplier<Runnable[]> fixMakespan = () -> {
            if (makespan.isFixed())
                return EMPTY;
            return branch(() -> makespan.getModelProxy().add(eq(makespan,makespan.min())));
        };

        model.runCP((cp) -> {
            DFSearch search = cp.dfSearch(and(branchPrecedences, fixMakespan));
            // print each solution found
            search.onSolution(() -> {
                System.out.println("makespan: " + makespan);
            });
            SearchStatistics stats = search.optimize(minimizeMakespan); // actually solve the problem
            System.out.println("stats: \n" + stats);
        });

    }

}
