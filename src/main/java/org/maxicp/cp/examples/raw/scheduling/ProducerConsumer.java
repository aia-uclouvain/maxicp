/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.scheduling.CPCumulFunction;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;

import java.io.File;
import java.util.Scanner;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

/**
 * RCPSP-CPR model.
 * (Koné, O.; Artigues, C.; Lopez, P.; and Mongeau, M. 2013. Comparison of mixed integer linear programming models
 * for the resource-constrained project scheduling problem with consumption and production of resources)
 *
 * @author Roger Kameugne, Pierre Schaus
 */
public class ProducerConsumer {

    public static void main(String[] args) throws Exception {

        String filename = args.length > 0 ? args[0] : "data/PRODUCER_CONSUMER/ConsProd_bl2002.dzn";
        int timeLimit = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        Instance instance = new Instance(filename);
        solve(instance, timeLimit);
    }

    public static void solve(Instance data, int timeLimit) {

        //Setting data:
        int nTasks = data.numberOfTasks;
        int nRenewableResources = data.numberOfResourcesClassic;
        int nReservoirResources = data.numberOfResourcesConsProd;
        int[] capRenewableResource = data.capacitiesClassic;
        int[] capReservoirResource = data.capacitiesConsProd;
        int[] durations = data.processingTimes;
        int[][] consRenewableResource = data.heightsClassic;
        int[][] consReservoirConsumption = data.heightsCons;
        int[][] consReservoirProduction = data.heightsProd;
        int[][] precedences = data.precedences;
        int horizon = data.horizon();
        String name = data.name;

        // Model:

        CPSolver cp = CPFactory.makeSolver();

        CPIntervalVar[] intervals = new CPIntervalVar[nTasks];
        CPCumulFunction[] renewableResource = new CPCumulFunction[nRenewableResources];
        CPCumulFunction[] reservoirResource = new CPCumulFunction[nReservoirResources];
        for (int i = 0; i < nRenewableResources; i++) {
            renewableResource[i] = flat();
        }
        for (int i = 0; i < nReservoirResources; i++) {
            if (capReservoirResource[i] > 0)
                reservoirResource[i] = step(cp, 0, capReservoirResource[i]);
            else
                reservoirResource[i] = flat();
        }

        for (int i = 0; i < nTasks; i++) {
            CPIntervalVar interval = makeIntervalVar(cp);
            interval.setEndMax(horizon);
            interval.setLength(durations[i]);
            interval.setPresent();
            intervals[i] = interval;
            for (int j = 0; j < nRenewableResources; j++) {
                if (consRenewableResource[j][i] > 0) {
                    renewableResource[j] = CPFactory.plus(renewableResource[j], pulse(interval, consRenewableResource[j][i]));
                }
            }
            for (int j = 0; j < nReservoirResources; j++) {
                if (consReservoirConsumption[j][i] > 0) {
                    reservoirResource[j] = CPFactory.plus(reservoirResource[j], stepAtStart(interval, consReservoirConsumption[j][i]));
                }
                if (consReservoirProduction[j][i] > 0) {
                    reservoirResource[j] = minus(reservoirResource[j], stepAtEnd(interval, consReservoirProduction[j][i]));
                }
            }
        }

        for (int i = 0; i < nTasks; i++) {
            for (int j = 0; j < nTasks; j++) {
                if (precedences[i][j] == 1) {
                    cp.post(endBeforeStart(intervals[i], intervals[j]));
                }
            }
        }

        for (int i = 0; i < nRenewableResources; i++) {
            cp.post(lessOrEqual(renewableResource[i], capRenewableResource[i]));
        }

        for (int i = 0; i < nReservoirResources; i++) {
            cp.post(alwaysIn(reservoirResource[i], 0, Integer.MAX_VALUE));
        }

        // Objective function
        CPIntVar makespan = makespan(intervals);
        Objective obj = cp.minimize(makespan);

        DFSearch dfs = new DFSearch(cp.getStateManager(), setTimes(intervals, i -> intervals[i].lengthMax()));
        dfs.onSolution(() -> {
            System.out.println("Makespan: " + makespan.min());
        });

        long begin = System.currentTimeMillis();
        SearchStatistics stats = dfs.optimize(obj, statistics -> statistics.isCompleted() || (System.currentTimeMillis() - begin) / 1000.0 > timeLimit);
        System.out.println(stats);
        System.out.println("time(s):" + (System.currentTimeMillis() - begin) / 1000.0);
    }

}

class Instance {
    public int[] capacitiesClassic;
    public int[] capacitiesConsProd;
    public int numberOfTasks;
    public int numberOfResourcesClassic;
    public int numberOfResourcesConsProd;
    public int heightsClassic[][];
    public int heightsCons[][];
    public int heightsProd[][];
    public int[] processingTimes;
    public int[][] precedences;
    int processingTimesSum;
    public String name;

    public Instance(String fileName) throws Exception {
        Scanner s = new Scanner(new File(fileName));

        numberOfResourcesClassic = Integer.parseInt(s.nextLine().split(" = ")[1].split(";")[0]);
        capacitiesClassic = new int[numberOfResourcesClassic];
        String[] cap_clas = s.nextLine().split("\\[ ")[1].split(" ];")[0].split(", ");
        for (int i = 0; i < numberOfResourcesClassic; i++) {
            capacitiesClassic[i] = Integer.parseInt(cap_clas[i]);
        }

        numberOfResourcesConsProd = Integer.parseInt(s.nextLine().split(" = ")[1].split(";")[0]);
        capacitiesConsProd = new int[numberOfResourcesConsProd];
        String[] cap_cons = s.nextLine().split("\\[ ")[1].split(" ];")[0].split(", ");
        for (int i = 0; i < numberOfResourcesConsProd; i++) {
            capacitiesConsProd[i] = Integer.parseInt(cap_cons[i]);
        }

        numberOfTasks = Integer.parseInt(s.nextLine().split(" = ")[1].split(";")[0]);
        processingTimes = new int[numberOfTasks];
        String[] dur = s.nextLine().split("\\[ ")[1].split(" ];")[0].split(", ");
        processingTimesSum = 0;
        for (int i = 0; i < numberOfTasks; i++) {
            processingTimes[i] = Integer.parseInt(dur[i]);
            processingTimesSum += processingTimes[i];
        }

        heightsClassic = new int[numberOfResourcesClassic][numberOfTasks];
        for (int j = 0; j < numberOfResourcesClassic; j++) {
            if (j < numberOfResourcesClassic - 1) {
                String[] cons = s.nextLine().split("\\| ")[1].split(", ");
                for (int i = 0; i < numberOfTasks; i++) {
                    heightsClassic[j][i] = Integer.parseInt(cons[i]);
                }
            } else {
                String[] cons = s.nextLine().split("\\| ")[1].split(" \\|")[0].split(", ");
                for (int i = 0; i < numberOfTasks; i++) {
                    heightsClassic[j][i] = Integer.parseInt(cons[i]);
                }
            }
        }

        heightsCons = new int[numberOfResourcesConsProd][numberOfTasks];
        for (int j = 0; j < numberOfResourcesConsProd; j++) {
            if (j < numberOfResourcesConsProd - 1) {
                String[] cons = s.nextLine().split("\\| ")[1].split(", ");
                for (int i = 0; i < numberOfTasks; i++) {
                    heightsCons[j][i] = Integer.parseInt(cons[i]);
                }
            } else {
                String[] cons = s.nextLine().split("\\| ")[1].split(" \\|")[0].split(", ");
                for (int i = 0; i < numberOfTasks; i++) {
                    heightsCons[j][i] = Integer.parseInt(cons[i]);
                }
            }
        }

        heightsProd = new int[numberOfResourcesConsProd][numberOfTasks];
        for (int j = 0; j < numberOfResourcesConsProd; j++) {
            if (j < numberOfResourcesConsProd - 1) {
                String[] cons = s.nextLine().split("\\| ")[1].split(", ");
                for (int i = 0; i < numberOfTasks; i++) {
                    heightsProd[j][i] = Integer.parseInt(cons[i]);
                }
            } else {
                String[] cons = s.nextLine().split("\\| ")[1].split(" \\|")[0].split(", ");
                for (int i = 0; i < numberOfTasks; i++) {
                    heightsProd[j][i] = Integer.parseInt(cons[i]);
                }
            }
        }

        precedences = new int[numberOfTasks][numberOfTasks];
        for (int i = 0; i < numberOfTasks; i++) {
            for (int j = 0; j < numberOfTasks; j++) {
                precedences[i][j] = -1;
            }
        }
        for (int i = 0; i < numberOfTasks; i++) {
            String line_preced = s.nextLine();
            if (!line_preced.contains("{  }")) {
                String preced_ = line_preced.split("\\{ ")[1].split("  },")[0];
                if (preced_ != "") {
                    String[] prd = preced_.split(", ");
                    for (int j = 0; j < prd.length; j++) {
                        int k = Integer.parseInt(prd[j]) - 1;
                        precedences[i][k] = 1;
                    }
                }
            }
        }

        name = fileName;
        s.close();
    }

    public int horizon() {
        return processingTimesSum;
    }
}