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
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.state.StateInt;
import org.maxicp.util.exception.InconsistencyException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.branch;
import static org.maxicp.search.Searches.setTimes;

/**
 * The JobShop Problem.
 * <a href="https://en.wikipedia.org/wiki/Job_shop_scheduling">Wikipedia.</a>
 *
 * @author Pierre Schaus
 */
public class JobShopWithDominance {

    public static CPIntervalVar[] flatten(CPIntervalVar[][] x) {
        return Arrays.stream(x).flatMap(Arrays::stream).toArray(CPIntervalVar[]::new);
    }

    public static void main(String[] args) {
        JobShopInstance instance = new JobShopInstance("data/JOBSHOP/jobshop-8-8-0");

        int nJobs = instance.nJobs;
        int nMachines = instance.nMachines;
        int[][] duration = instance.duration;
        int[][] machine = instance.machine;

        CPSolver cp = CPFactory.makeSolver();

        StateInt[] currentTask = new StateInt[nJobs];
        StateInt currentMakespan = cp.getStateManager().makeStateInt(0);

        for (int j = 0; j < nJobs; j++) {
            currentTask[j] = cp.getStateManager().makeStateInt(0);
        }

        // create activities
        CPIntervalVar[][] activities = new CPIntervalVar[nJobs][nMachines];
        for (int j = 0; j < nJobs; j++) {
            for (int m = 0; m < nMachines; m++) {
                activities[j][m] = makeIntervalVar(cp, false, duration[j][m], duration[j][m]);
            }
        }

        // precedence constraints on each job
        for (int j = 0; j < nJobs; j++) {
            for (int m = 1; m < nMachines; m++) {
                cp.post(endBeforeStart(activities[j][m - 1], activities[j][m]));
            }
        }

        // no overlap between the activities on the same machine
        for (int m = 0; m < nMachines; m++) {
            ArrayList<CPIntervalVar> machineActivities = new ArrayList<>();
            for (int j = 0; j < nJobs; j++) {
                for (int i = 0; i < nMachines; i++) {
                    if (machine[j][i] == m) {
                        machineActivities.add(activities[j][i]);
                    }
                };
            }
            cp.post(nonOverlap(machineActivities.toArray(new CPIntervalVar[0])));
        }


        CPIntervalVar[] lasts = Arrays.stream(activities)
                .map(job -> job[nMachines - 1])
                .toArray(CPIntervalVar[]::new);
        CPIntVar makespan = CPFactory.makespan(lasts);

        Objective obj = cp.minimize(makespan);

        CPIntervalVar[] allActivities = flatten(activities);

        DFSearch dfs = CPFactory.makeDfs(cp,() -> {
            // find the job with the smallest current task end time

            record Alternative(int makespan, Runnable action) {}

            List<Alternative> branches = new LinkedList<>();

            boolean allJobsDone = true;
            for (int j = 0; j < nJobs; j++) {
                final int job = j;
                int taskIdx = currentTask[job].value();
                if (taskIdx < nMachines) {
                    CPIntervalVar task = activities[job][taskIdx];
                    if (task.endMin() >= currentMakespan.value()) {
                        branches.add(new Alternative(task.endMin(), () -> {
                            // assign the start time to its minimum
                            task.setStart(task.startMin());
                            cp.fixPoint();
                            // update current task and makespan
                            currentMakespan.setValue(Math.max(currentMakespan.value(), task.endMin()));
                            currentTask[job].increment();
                        }));
                    }
                    allJobsDone = false;
                }
            }
            branches.sort(Comparator.comparingInt(Alternative::makespan));
            Runnable[] branchesArray =
                    branches.stream()
                            .map(Alternative::action)
                            .toArray(Runnable[]::new);
            if (branchesArray.length == 0 && !allJobsDone) {
                throw InconsistencyException.INCONSISTENCY;
            }
           return branchesArray;

        });

        // for each job, I can tell at what task I am and the current makespan





        dfs.onSolution(() -> {
            System.out.println("makespan:" + makespan);
        });
        SearchStatistics stats = dfs.optimize(obj);
        System.out.format("Statistics: %s\n", stats);
    }

    private static class JobShopInstance {

        public int nJobs;
        public int nMachines;
        public int[][] duration;
        public int[][] machine;

        public JobShopInstance(String path) {
            try {
                FileInputStream istream = new FileInputStream(path);
                BufferedReader in = new BufferedReader(new InputStreamReader(istream));
                in.readLine();
                in.readLine();
                in.readLine();
                StringTokenizer tokenizer = new StringTokenizer(in.readLine());
                nJobs = Integer.parseInt(tokenizer.nextToken());
                nMachines = Integer.parseInt(tokenizer.nextToken());
                duration = new int[nJobs][nMachines];
                machine = new int[nJobs][nMachines];
                for (int i = 0; i < nJobs; i++) {
                    tokenizer = new StringTokenizer(in.readLine());
                    for (int j = 0; j < nMachines; j++) {
                        machine[i][j] = Integer.parseInt(tokenizer.nextToken());
                        duration[i][j] = Integer.parseInt(tokenizer.nextToken());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }


}