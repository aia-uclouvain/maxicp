/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

import org.maxicp.cp.engine.constraints.scheduling.NoOverlap;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;

import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.search.*;
import org.maxicp.util.exception.InconsistencyException;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The JobShop Problem.
 * <a href="https://en.wikipedia.org/wiki/Job_shop_scheduling">Wikipedia.</a>
 *
 * @author Pierre Schaus
 */
public class JobShop {

    public static CPIntervalVar[] flatten(CPIntervalVar[][] x) {
        return Arrays.stream(x).flatMap(Arrays::stream).toArray(CPIntervalVar[]::new);
    }

    public static void main(String[] args) {
        JobShopInstance instance = new JobShopInstance("data/JOBSHOP/ft10.txt");

        int nJobs = instance.nJobs;
        int nMachines = instance.nMachines;
        int[][] duration = instance.duration;
        int[][] machine = instance.machine;

        CPSolver cp = CPFactory.makeSolver();

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

        CPIntervalVar [][] toRank = new CPIntervalVar[nMachines][];

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
            CPIntervalVar [] onMachine = machineActivities.toArray(new CPIntervalVar[0]);
            cp.post(nonOverlap(onMachine));
            toRank[m] = onMachine;
        }


        CPIntervalVar[] lasts = Arrays.stream(activities)
                .map(job -> job[nMachines - 1])
                .toArray(CPIntervalVar[]::new);
        CPIntVar makespan = CPFactory.makespan(lasts);

        Objective obj = cp.minimize(makespan);

        CPIntervalVar[] allActivities = flatten(activities);


        DFSearch dfs = CPFactory.makeDfs(cp,
                and(Rank.rank(toRank),
                        () -> makespan.isFixed() ? EMPTY: branch(() -> cp.post(le(makespan, makespan.min())))
                ));


        //DFSearch dfs = CPFactory.makeDfs(cp, setTimes(allActivities));

        dfs.onSolution(() -> {
            System.out.println("=========================>makespan:" + makespan);
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