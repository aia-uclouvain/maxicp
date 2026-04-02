/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.jobshop;

import org.maxicp.cp.CPFactory;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

import org.maxicp.cp.engine.constraints.scheduling.MinMakespan;
import org.maxicp.cp.engine.constraints.scheduling.PrecedenceGraph;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;

import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.search.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;

/**
 * The JobShop Problem modeled with a {@link PrecedenceGraph}.
 * <p>
 * A single precedence graph handles both job-internal precedences and
 * machine-ordering decisions (via {@link Rank} branching).
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

        // Create activities: activities[j][o] is job j, operation o
        CPIntervalVar[][] activities = new CPIntervalVar[nJobs][nMachines];
        for (int j = 0; j < nJobs; j++) {
            for (int o = 0; o < nMachines; o++) {
                activities[j][o] = makeIntervalVar(cp, false, duration[j][o], duration[j][o]);
            }
        }

        // Flatten all activities into a single array (global index = j * nMachines + o)
        CPIntervalVar[] allActivities = flatten(activities);

        // Single precedence graph over all activities
        PrecedenceGraph graph = new PrecedenceGraph(allActivities);
        // Disable O(n²) detectable-precedence detection — NoOverlap handles machine disjunctions
        graph.setDetectPrecedences(false);
        cp.post(graph);

        // Job precedences: operations within each job must be sequential
        for (int j = 0; j < nJobs; j++) {
            for (int o = 1; o < nMachines; o++) {
                graph.addPrecedence(j * nMachines + (o - 1), j * nMachines + o);
            }
        }

        CPIntervalVar[] lasts = Arrays.stream(activities)
                .map(job -> job[nMachines - 1])
                .toArray(CPIntervalVar[]::new);
        CPIntVar makespan = CPFactory.makespan(lasts);

        // Build machine groups and post no-overlap constraints per machine
        int[][] machineIndices = new int[nMachines][];
        for (int m = 0; m < nMachines; m++) {
            ArrayList<Integer> indices = new ArrayList<>();
            for (int j = 0; j < nJobs; j++) {
                for (int o = 0; o < nMachines; o++) {
                    if (machine[j][o] == m) {
                        indices.add(j * nMachines + o);
                    }
                }
            }
            machineIndices[m] = indices.stream().mapToInt(Integer::intValue).toArray();
            // NoOverlap per machine for strong edge-finding propagation
            CPIntervalVar[] onMachine = new CPIntervalVar[machineIndices[m].length];
            for (int k = 0; k < onMachine.length; k++) {
                onMachine[k] = allActivities[machineIndices[m][k]];
            }
            cp.post(noOverlap(onMachine));
            cp.post(new MinMakespan(graph, makespan, onMachine));
        }


        Objective obj = cp.minimize(makespan);

        // Search: rank branching on the precedence graph + makespan tightening
        /*
        DFSearch dfs = CPFactory.makeDfs(cp,
                and(new Rank(graph, machineIndices),
                        () -> makespan.isFixed() ? EMPTY : branch(() -> cp.post(le(makespan, makespan.min())))
                ));*/
        DFSearch dfs = CPFactory.makeDfs(cp,fds(allActivities));

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