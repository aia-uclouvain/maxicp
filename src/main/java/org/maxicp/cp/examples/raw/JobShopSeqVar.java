/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.Factory.eq;
import static org.maxicp.search.Searches.*;
import static org.maxicp.search.Searches.EMPTY;

/**
 * The JobShop Problem.
 * <a href="https://en.wikipedia.org/wiki/Job_shop_scheduling">Wikipedia.</a>
 *
 * @author Pierre Schaus
 */
public class JobShopSeqVar {

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

        CPSeqVar[] seqVars = new CPSeqVar[nMachines];
        CPIntervalVar[][] machineIntervals = new CPIntervalVar[nMachines][];
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
            machineIntervals[m] = machineActivities.toArray(new CPIntervalVar[0]);
            seqVars[m] = nonOverlapSequence(machineIntervals[m]);
        }


        CPIntervalVar[] lasts = Arrays.stream(activities)
                .map(job -> job[nMachines - 1])
                .toArray(CPIntervalVar[]::new);
        CPIntVar makespan = CPFactory.makespan(lasts);

        Objective obj = cp.minimize(makespan);


        // ------- search on seq vars ------

        Supplier<Runnable[]> fixMakespan = () -> {
            if (makespan.isFixed())
                return EMPTY;
            return branch(() -> makespan.getModelProxy().add(eq(makespan,makespan.min())));
        };

        Supplier<Runnable[]>[] rankers = new Supplier[nMachines];


        for (int m = 0; m < nMachines; m++) {
            CPIntervalVar[] intervals = machineIntervals[m];
            rankers[m] = rank(seqVars[m],pred -> pred < intervals.length ? intervals[pred].endMin(): 0);
        }

        DFSearch dfs = CPFactory.makeDfs(cp, and(and(rankers),fixMakespan));

        long t0 = System.currentTimeMillis();
        dfs.onSolution(() -> {
            System.out.println("t="+((System.currentTimeMillis()-t0)/1000.0)+"[s] makespan:" + makespan);
        });
        SearchStatistics stats = dfs.optimize(obj);
        System.out.format("Statistics: %s\n", stats);
    }

    public static Supplier<Runnable[]> rank(CPSeqVar seqVar, Function<Integer, Integer> predNodeHeuristic) {
        // check that all the nodes are required
        if (IntStream.range(0, seqVar.nNode()).anyMatch(n -> !seqVar.isNode(n,SeqStatus.REQUIRED))) {
            throw new IllegalArgumentException("rank requires all nodes to be required");
        }
        CPSolver cp =seqVar.getSolver();
        int[] nodes = new int[seqVar.nNode()];
        return () -> {
            // select the non-inserted node with the fewest number of insertions (first fail)
            int nInsertables = seqVar.fillNode(nodes, SeqStatus.INSERTABLE);
            if (nInsertables == 0) {
                return EMPTY; // no node to insert -> solution found
            }
            int node = selectMin(nodes,nInsertables, n -> true, n -> seqVar.nInsert(n)).getAsInt();
            int nInsert = seqVar.fillInsert(node, nodes);
            Runnable[] branches = new Runnable[nInsert];
            Integer[] heuristicPred = new Integer[nInsert];

            // insert the node at every feasible insertion in the sequence
            for (int j = 0; j < nInsert; j++) {
                int pred = nodes[j]; // predecessor for the node
                heuristicPred[j] = predNodeHeuristic.apply(pred);
                branches[j] = () -> cp.post(insert(seqVar, pred, node));
            }
            Map<Runnable,Integer> predHeuristic = new HashMap<>();
            for (int j = 0; j < nInsert; j++) {
                predHeuristic.put(branches[j], heuristicPred[j]);
            }
            // sort the branches according to the heuristic on the predecessor
            Arrays.sort(branches, Comparator.comparingInt(predHeuristic::get));
            return branches;
        };
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

