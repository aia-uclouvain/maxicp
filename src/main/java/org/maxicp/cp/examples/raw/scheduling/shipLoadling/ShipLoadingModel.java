/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.scheduling.shipLoadling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.scheduling.CPCumulFunction;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;

import java.util.ArrayList;
import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.and;
import static org.maxicp.search.Searches.firstFail;

/**
 * Ship Loading Problem model.
 *
 * @author Roger Kameugne
 */
public class ShipLoadingModel {
    int nbTasks;
    int nbResources;
    int capacityResource;
    int[] sizes;
    ArrayList<Integer>[] successors;
    int horizon;
    String name;
    public double elapsedTime;
    public int numberOfNodes;
    public int numberOfFails;
    int makespan = -1;
    int[] startSolution;
    int[] endSolution;
    int[] heightSolution;
    public ShipLoadingModel(ShipLoadingInstance data) throws Exception {
        // Data:
        nbTasks = data.nbTasks;
        nbResources = data.nbResources;
        capacityResource = data.resourceCapacity;
        sizes = data.sizes;
        successors = data.successors;
        horizon = data.horizon;
        name = data.name;

        CPSolver cp = CPFactory.makeSolver();

        // Variables:
        CPIntervalVar[] intervals = new CPIntervalVar[nbTasks];
        CPIntVar[] starts = new CPIntVar[nbTasks];
        CPIntVar[] ends = new CPIntVar[nbTasks];
        CPIntVar[] height = new CPIntVar[nbTasks];
        CPCumulFunction resource = flat();
        for (int i = 0; i < nbTasks; i++) {
            CPIntervalVar interval = makeIntervalVar(cp);
            interval.setEndMax(horizon);
            interval.setLengthMin(1); // remove
            interval.setLengthMax(sizes[i]);
            interval.setPresent();
            starts[i] = CPFactory.start(interval);
            ends[i] = CPFactory.end(interval);
            resource = CPFactory.plus(resource, pulse(interval, 1, Math.min(capacityResource, sizes[i])));
            intervals[i] = interval;
        }

        // Precedence and size constraints:
        for (int i = 0; i < nbTasks; i++) {
            for (int k : successors[i]) {
                cp.post(endBeforeStart(intervals[i], intervals[k]));
            }
            height[i] = resource.heightAtStart(intervals[i]);
            if (height[i].min() * (ends[i].max() - starts[i].min()) < sizes[i]) {
                int upd = ceilFunction(sizes[i], ends[i].max() - starts[i].min());
                height[i].removeBelow(upd);
            }
            int val = ceilFunction( sizes[i], height[i].max());
            intervals[i].setLengthMin(val);
        }

        // Resource constraint:
        cp.post(lessOrEqual(resource, capacityResource));

        // Objective
        CPIntVar mksp = maximum(ends);
        Objective obj = cp.minimize(mksp);

        // Search:
        DFSearch dfs = CPFactory.makeDfs(cp, and(firstFail(starts), firstFail(ends)));

        // Solution management:
        startSolution = new int[nbTasks];
        endSolution = new int[nbTasks];
        heightSolution = new int[nbTasks];
        dfs.onSolution(() -> {
            makespan = mksp.min();
            for (int i = 0; i < nbTasks; i++) {
                startSolution[i] = starts[i].min();
                endSolution[i] = ends[i].min();
                heightSolution[i] = sizes[i]/(endSolution[i] - startSolution[i]);
            }
        });

        //Launching search:
        long begin = System.currentTimeMillis();
        SearchStatistics stats = dfs.optimize(obj);
        elapsedTime = (System.currentTimeMillis() - begin)/1000.0;
        if (stats.isCompleted()) {
            numberOfNodes = stats.numberOfNodes();
            numberOfFails = stats.numberOfFailures();
        }
    }

    private int ceilFunction(int n, int p) {
        for (int k = n/p; k <= n; k++) {
            if (k > 1 && n % k == 0)
                return k;
        }
        return 1;
    }

    public void printSolution() {
        System.out.println(name + " | " + nbTasks + " | " + makespan + " | " + elapsedTime + " | " + numberOfFails + " | " + numberOfNodes + " | " + Arrays.toString(startSolution) + " | " + Arrays.toString(endSolution)+ " | " + Arrays.toString(heightSolution));
    }

    public static void main(String[] args) throws Exception{
        ShipLoadingInstance data = new ShipLoadingInstance(args[0]);
        ShipLoadingModel sample = new ShipLoadingModel(data);
        sample.printSolution();
    }
}