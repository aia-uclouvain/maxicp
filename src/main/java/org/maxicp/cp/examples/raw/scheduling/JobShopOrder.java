/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.LessOrEqual;
import org.maxicp.cp.engine.constraints.scheduling.NoOverlap;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.function.Supplier;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

/**
 * The JobShop Problem.
 * <a href="https://en.wikipedia.org/wiki/Job_shop_scheduling">Wikipedia.</a>
 *
 * @author Pierre Schaus
 */
public class JobShopOrder {

    public static CPIntervalVar[] flatten(CPIntervalVar[][] x) {
        return Arrays.stream(x).flatMap(Arrays::stream).toArray(CPIntervalVar[]::new);
    }

    public static CPIntVar[] flatten(CPIntVar[][] x) {
        return Arrays.stream(x).flatMap(Arrays::stream).toArray(CPIntVar[]::new);
    }

    public static void main(String[] args) {
        // Reading data:
        try {
            FileInputStream istream = new FileInputStream("data/JOBSHOP/jobshop-8-8-0");
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

            CPIntVar[][] start = new CPIntVar[nJobs][nMachines];
            CPIntVar[][] end = new CPIntVar[nJobs][nMachines];
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
                    start[i][j] = startOr(activities[i][j],0);
                    end[i][j] = endOr(activities[i][j], 0);
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

            // -------
            CPIntVar[] order = new CPIntVar[nJobs * nMachines];
            for (int i = 0; i < nJobs * nMachines; i++) {
                order[i] = makeIntVar(cp, nJobs * nMachines);
            }
            CPIntVar[] startOrder = new CPIntVar[nMachines * nJobs];
            CPIntVar[] allStart = flatten(start);
            System.out.printf("allStart: %s\n", Arrays.toString(allStart));
            for (int i = 0; i < nJobs * nMachines; i++) {
                startOrder[i] = element(allStart, order[i]);
            }
            for (int i = 0; i < nJobs * nMachines - 1; i++) {
                cp.post(new LessOrEqual(startOrder[i], startOrder[i + 1]));
            }

            cp.post(allDifferent(order));
            // -------



            CPIntVar makespan = makespan(lasts);

            cp.post(CPFactory.eq(makespan,117));

            //Objective obj = cp.minimize(makespan);

            CPIntervalVar [] allActivities = flatten(activities);

            Supplier<Runnable[]> fixMakespan = () -> {
                if (makespan.isFixed())
                    return EMPTY;
                return branch(() -> {
                    makespan.getModelProxy().add(new Eq(makespan,makespan.min()));
                });
            };

            //DFSearch dfs = CPFactory.makeDfs(cp, and(branchOnPresentStarts(allActivities)));
            CPBoolVar[] precedencesArray = precedences.toArray(new CPBoolVar[0]);

            Supplier<Runnable[]> fixPrecedences = firstFail(precedencesArray);
            DFSearch dfs = CPFactory.makeDfs(cp, and(fixPrecedences,fixMakespan));

            Supplier<Runnable[]> fixPartialOrder = firstFail(order);
            //DFSearch dfs = CPFactory.makeDfs(cp, and(fixPartialOrder,fixPrecedences,fixMakespan));


            //DFSearch dfs = CPFactory.makeDfs(cp, and(fixPartialOrder,fixMakespan));


            dfs.onSolution(() -> {
                System.out.println("makespan:" + makespan);
                System.out.printf("starts: %s\n", Arrays.toString(allStart));
                System.out.printf("order: %s\n", Arrays.toString(order));
            });

            SearchStatistics stats = dfs.solve();

            //SearchStatistics stats = dfs.optimize(obj);

            System.out.format("Statistics: %s\n", stats);

        } catch (IOException | InconsistencyException e) {
            e.printStackTrace();
        }
    }
}