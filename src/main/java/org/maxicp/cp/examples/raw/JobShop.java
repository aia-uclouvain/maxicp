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
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;
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
        // Reading data:
        try {
            FileInputStream istream = new FileInputStream("data/JOBSHOP/jobshop-9-9-0");
            BufferedReader in = new BufferedReader(new InputStreamReader(istream));
            in.readLine();
            in.readLine();
            in.readLine();
            StringTokenizer tokenizer = new StringTokenizer(in.readLine());
            int nJobs = Integer.parseInt(tokenizer.nextToken());
            int nMachines = Integer.parseInt(tokenizer.nextToken());

            System.out.println(nJobs + " " + nMachines);
            int[][] duration = new int[nJobs][nMachines];
            int[][] machine = new int[nJobs][nMachines];

            int horizon = 0;
            for (int i = 0; i < nJobs; i++) {
                tokenizer = new StringTokenizer(in.readLine());
                for (int j = 0; j < nMachines; j++) {
                    machine[i][j] = Integer.parseInt(tokenizer.nextToken());
                    duration[i][j] = Integer.parseInt(tokenizer.nextToken());

                    horizon += duration[i][j];
                }
            }

            CPSolver cp = CPFactory.makeSolver();

            CPIntervalVar[][] activities = new CPIntervalVar[nJobs][nMachines];
            ArrayList<CPIntVar>[] startOnMachine = new ArrayList[nMachines];
            ArrayList<Integer>[] durationsOnMachine = new ArrayList[nMachines];
            ArrayList<CPIntervalVar>[] activitiesOnMachine = new ArrayList[nMachines];

            for (int m = 0; m < nMachines; m++) {
                startOnMachine[m] = new ArrayList<CPIntVar>();
                durationsOnMachine[m] = new ArrayList<Integer>();
                activitiesOnMachine[m] = new ArrayList<>();
            }

            CPIntervalVar[] lasts = new CPIntervalVar[nJobs];
            for (int i = 0; i < nJobs; i++) {
                for (int j = 0; j < nMachines; j++) {

                    activities[i][j] = makeIntervalVar(cp);
                    activities[i][j].setLengthMax(duration[i][j]);
                    activities[i][j].setLengthMin(duration[i][j]);
                    activities[i][j].setPresent();

                    int m = machine[i][j];
                    activitiesOnMachine[m].add(activities[i][j]);

                    if (j > 0) {
                        // precedence constraint
                        cp.post(endBeforeStart(activities[i][j - 1], activities[i][j]));
                    }
                }
                lasts[i] = activities[i][nMachines - 1];
            }

            ArrayList<CPBoolVar> precedences = new ArrayList<>();

            for (int m = 0; m < nMachines; m++) {
                NoOverlap nonOverlap = nonOverlap(activitiesOnMachine[m].toArray(new CPIntervalVar[0]));
                cp.post(nonOverlap);
                precedences.addAll(Arrays.asList(nonOverlap.precedenceVars()));
            }

            CPIntVar makespan = makespan(lasts);

            Objective obj = cp.minimize(makespan);

            CPIntervalVar [] allActivities = flatten(activities);

            Supplier<Runnable[]> fixMakespan = () -> {
                if (makespan.isFixed())
                    return EMPTY;
                return branch(() -> {
                    makespan.getModelProxy().add(new Eq(makespan,makespan.min()));
                });
            };

            CPBoolVar[] precedencesArray = precedences.toArray(new CPBoolVar[0]);

            Supplier<Runnable[]> fixPrecedences = conflictOrderingSearch(Searches.minDomVariableSelector(precedencesArray), x -> x.min());
            DFSearch dfs = CPFactory.makeDfs(cp, and(fixPrecedences,fixMakespan));

            dfs.onSolution(() -> {
                    System.out.println("makespan:" + makespan);
            });

            SearchStatistics stats = dfs.optimize(obj);

            System.out.format("Statistics: %s\n", stats);

        } catch (IOException | InconsistencyException e) {
            e.printStackTrace();
        }
    }
}