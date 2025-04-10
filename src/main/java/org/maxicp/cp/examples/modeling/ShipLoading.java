/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import static org.maxicp.search.Searches.and;
import static org.maxicp.search.Searches.firstFail;

import static org.maxicp.modeling.Factory.*;

/**
 * !!! This model does not work correctly, there is a bug with heightAtStart in the modeling  !!!
 * Ship Loading Problem.
 *
 * The problem is to find a schedule that minimizes the time to unload and
 * to load a ship. The work contains a set of 34 elementary tasks. Each task has to be handled by
 * a given number of people and during a given period of time. For each task, only
 * the associated surface is known (i.e., the product of the task duration by the needed amount of
 * resource).
 *
 * This problem was described in the paper:
 * Aggoun, A., & Beldiceanu, N. (1993). Extending CHIP in order to solve complex scheduling and placement problems.
 * Mathematical and computer modelling, 17(7), 57-73.
 *
 * @author Roger Kameugne, Pierre Schaus
 */
public class ShipLoading {



    public ShipLoading(ShipLoadingInstance data) throws Exception {

        ModelDispatcher model = makeModelDispatcher();

        // Variables:
        IntervalVar[] intervals = new IntervalVar[data.nbTasks];
        IntExpression[] starts = new IntExpression[data.nbTasks];
        IntExpression[] ends = new IntExpression[data.nbTasks];
        IntExpression[] length = new IntExpression[data.nbTasks];
        IntExpression[] height = new IntExpression[data.nbTasks];
        CumulFunction resource = flat();

        for (int i = 0; i < data.nbTasks; i++) {
            // intervalVar(int startMin, int endMax, int duration, boolean isPresent)
            // TODO: min lenght is 1
            IntervalVar interval = model.intervalVar(0, data.horizon, 0, data.horizon, 1, data.sizes[i], true);
            starts[i] = start(interval);
            ends[i] = end(interval);
            length[i] = length(interval);
            intervals[i] = interval;

            resource = sum(resource, pulse(interval, 1, Math.min(data.resourceCapacity, data.sizes[i])));
            height[i] = resource.heightAtStart(intervals[i]);

        }


        for (int i = 0; i < data.nbTasks; i++) {

            // Precedence constraints:
            for (int k : data.successors[i]) {
                model.add(endBeforeStart(intervals[i], intervals[k]));
            }

            // Size constraints:
            //for (int k : data.successors[i]) {
                model.add(eq(mul(length[i], height[i]),data.sizes[i]));
            //}
        }

        // Resource constraint:
        model.add(le(resource, data.resourceCapacity));

        System.out.println("Resource capacity: " + data.resourceCapacity);

        // Objective
        IntExpression makespan = max(ends);

        Objective obj = minimize(makespan);

        ConcreteCPModel cp = model.cpInstantiate();


        // Search:
        DFSearch dfs = cp.dfSearch(and(firstFail(starts), firstFail(ends)));

        // Solution management:
        dfs.onSolution(() -> {
            // verify solution size constraints
            for (int i = 0; i < data.nbTasks; i++) {
                int s = starts[i].min();
                int e = ends[i].max();
                int l = length[i].min();
                int h = height[i].min();
                int surface = l*h;
                System.out.println("task " + i + ": start=" + s + ", end=" + e + ", length=" + l + ", height=" + h + ", surface=" + surface+ ", size=" + data.sizes[i]);
                assert(surface == data.sizes[i]);
            }
            System.out.println("solution:");
            System.out.println("heights:"+Arrays.toString(height));
            System.out.println("starts:"+Arrays.toString(Arrays.stream(starts).map(x -> x.min()).toArray()));
            System.out.println("ends:"+Arrays.toString(Arrays.stream(ends).map(x -> x.min()).toArray()));

            // System.out.println("lengths:"+Arrays.toString(length));
            System.out.println("makespan: " + makespan);
        });

        //Launching search:
        long begin = System.currentTimeMillis();
        SearchStatistics stats = dfs.optimize(obj);
        System.out.println(stats);
        long time = (System.currentTimeMillis() - begin)/1000;
        System.out.println("time(s):" + time);
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