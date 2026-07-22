/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.examples.utils.SMICInstance;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

/**
 * A Single Machine with Inventory Constraints (SMIC) problem consists
 * of scheduling a set of J of n jobs split into in two:
 * - the loading jobs J+ and
 * - the unloading jobs J-.
 * Each job has a release date, a processing time and an inventory modification
 * delta with
 * delta > 0 for loading job and delta < 0 for unloading job.
 * An initial inventory and the capacity of the inventory storage are given.
 * The aim is to sequence the jobs such that the makespan is minimized
 * while the inventory is range between 0 and the capacity of the inventory.
 */
public class SMIC {

    public static void main(String[] args) throws Exception {
        String filename = args.length > 0 ? args[0] : "data/SMIC/data10_3.txt";
        int searchTime = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        SMICInstance data = new SMICInstance(filename);
        SMIC model = new SMIC(data, searchTime);

        System.out.println(data.name + " | " + model.optimalSolution + " | "
                + model.elapsedTime + " | " + model.failures + " | " + model.status + " | maxiCP_STK");
    }

    final SMICInstance data;
    // Best solution:
    public int optimalSolution;
    public int[] solution;
    public double elapsedTime;
    public boolean status;
    public int failures;
    final double timeLimit;

    public SMIC(SMICInstance data, double timeLimit) {
        this.data = data;
        this.timeLimit = timeLimit;

        // Initialize the model
        ModelDispatcher model = makeModelDispatcher();

        // Variables of the model
        IntervalVar[] intervals = new IntervalVar[data.nbJob];
        IntExpression[] starts = new IntExpression[data.nbJob];
        IntExpression[] ends = new IntExpression[data.nbJob];
        CumulFunction cumul = step(model, 0, data.initInventory);

        for (int i = 0; i < data.nbJob; i++) {
            IntervalVar interval = model.intervalVar(0, data.horizon, data.processing[i], true);
            model.add(startAfter(interval, data.release[i]));
            starts[i] = start(interval);
            ends[i] = end(interval);
            intervals[i] = interval;
            if (data.type[i] == 1) {
                cumul = sum(cumul, stepAtStart(intervals[i], data.inventory[i], data.inventory[i]));
            } else {
                cumul = minus(cumul, stepAtStart(intervals[i], data.inventory[i], data.inventory[i]));
            }
        }

        // Constraints
        model.add(alwaysIn(cumul, 0, data.capaInventory));
        model.add(noOverlap(intervals));

        // Objective
        IntExpression makespan = max(ends);
        Objective obj = minimize(makespan);

        // Solution storage
        solution = new int[data.nbJob];

        model.runCP((cp) -> {
            // Search
            DFSearch dfs = cp.dfSearch(Searches.fds(intervals));

            dfs.onSolution(() -> {
                System.out.println("solution found makespan: " + makespan.min());
                optimalSolution = makespan.min();
                for (int i = 0; i < data.nbJob; i++) {
                    solution[i] = intervals[i].startMin();
                }
            });

            // Launching search
            long begin = System.currentTimeMillis();
            SearchStatistics stats = dfs.optimize(obj,
                    statistics -> (System.currentTimeMillis() - begin) / 1000.0 > timeLimit);
            elapsedTime = (System.currentTimeMillis() - begin) / 1000.0;
            failures = stats.numberOfFailures();
            status = stats.isCompleted();
        });
    }
}