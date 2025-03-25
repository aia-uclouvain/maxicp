/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.scheduling.CPCumulFunction;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.and;
import static org.maxicp.search.Searches.firstFail;

/**
 * Ship Loading Problem model.
 *
 * @author Roger Kameugne
 */
public class ShipLoading {

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

    public ShipLoading(ShipLoadingInstance data) throws Exception {
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
                cp.post(ge(height[i],upd));
            }
            int val = ceilFunction( sizes[i], height[i].max());
            cp.post(ge(length(intervals[i]), val));
        }

        // Resource constraint:
        cp.post(le(resource, capacityResource));

        // Objective
        CPIntVar makespan = max(ends);
        Objective obj = cp.minimize(makespan);

        // Search:
        DFSearch dfs = CPFactory.makeDfs(cp, and(firstFail(starts), firstFail(ends)));

        // Solution management:
        dfs.onSolution(() -> {
            System.out.println("makespan: " + makespan);
        });

        //Launching search:
        long begin = System.currentTimeMillis();
        SearchStatistics stats = dfs.optimize(obj);
        System.out.println(stats);
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

    public static void main(String[] args) throws Exception{
        ShipLoadingInstance data = new ShipLoadingInstance("data/SHIP_LOADING/shipLoading1.txt");
        ShipLoading sl = new ShipLoading(data);
    }
}

/**
 * Ship Loading Problem instance.
 *
 * @author Roger Kameugne
 */
class ShipLoadingInstance {
    public int nbTasks;
    public int nbResources;
    public int resourceCapacity;
    public int[] sizes;
    public ArrayList<Integer>[] successors;
    public int horizon;
    public String name;
    int sumSizes;

    public ShipLoadingInstance (String fileName) throws Exception {
        Scanner s = new Scanner(new File(fileName)).useDelimiter("\\s+");
        while (!s.hasNextInt()) s.nextLine();
        nbTasks = s.nextInt();
        nbResources = s.nextInt();
        resourceCapacity = s.nextInt();
        sizes = new int[nbTasks];
        successors = new ArrayList[nbTasks];
        sumSizes = 0;
        for (int i = 0; i < nbTasks; i++) {
            successors[i] = new ArrayList<>();
            sizes[i] = s.nextInt();
            sumSizes += sizes[i];
            int nbSucc = s.nextInt();
            if (nbSucc > 0) {
                for (int j = 0; j < nbSucc; j++) {
                    int succ = s.nextInt();
                    successors[i].add(succ - 1);
                }
            }
        }
        name = fileName;
        horizon = sumSizes;
        s.close();
    }
}