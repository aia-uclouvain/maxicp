/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

/**
 * The <a href="https://citeseerx.ist.psu.edu/document?repid=rep1&type=pdf&doi=a67f6a101532dcd5b70d1469072ccd8facf2d1b2">Cumulative Scheduling Problem</a> (CuSP)
 * consists in scheduling a set tasks that share a resource with a fixed capacity. The objective is to minimize the
 * makespan such that the capacity is not violated. Each task has a fixed duration and consumes a fixed amount of the
 * resource.
 */
public class CuSP {
    ModelDispatcher baseModel;
    IntervalVar[] intervals;
    CumulFunction resource;
    IntExpression makespan;
    Objective obj;

    public CuSP(CuSPInstance data) {
        baseModel = makeModelDispatcher();

        // Variables
        intervals = baseModel.intervalVarArray(data.nTasks,true);
        resource = flat();
        for (int i = 0; i < data.nTasks; i++) {
            baseModel.add(length(intervals[i], data.durations[i]));
            resource = sum(resource, pulse(intervals[i], data.demands[i]));
        }

        baseModel.add(Factory.le(resource, data.capacity));

        makespan = max(Arrays.stream(intervals).map(task -> endOr(task, 0)).toArray(IntExpression[]::new));
        obj = minimize(makespan);
    }

    private void solve(int searchTime) {
        baseModel.runCP((cp) -> {
            Supplier<Runnable[]> fixMakespan = () -> {
                if (makespan.isFixed())
                    return EMPTY;
                return branch(() -> baseModel.add(eq(makespan, makespan.min())));
            };

            Supplier<Runnable[]> branching = and(branchOnPresentStarts(intervals), fixMakespan);

            DFSearch search = cp.dfSearch(branching);

            long startTime = System.currentTimeMillis();
            search.onSolution(() -> {
                System.out.println("Solution found:");
                System.out.println("Makespan: " + makespan);
                System.out.println("Solution: " + Arrays.stream(intervals).map(i -> "" + i.startMin()).collect(Collectors.joining(", ")));
                System.out.println("Elapsed time: " + ((System.currentTimeMillis() - startTime)/1000.0));
            });
            SearchStatistics stats = search.optimize(obj, statistics -> (System.currentTimeMillis() - startTime) / 1000.0 > searchTime); // actually solve the problem
            System.out.println("stats: \n" + stats);
        });
    }

    public static void main(String[] args) throws Exception{
        if (args.length < 1)  throw new IllegalStateException("No instance file provided");
        final String filename = args[0];
        System.out.println("Reading file " + filename);
        CuSPInstance instance = new CuSPInstance(filename);

        int searchTime = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        CuSP model = new CuSP(instance);
        model.solve(searchTime);
    }

    public static class CuSPInstance {
        public final int nTasks;
        public final int capacity;
        public final int[] durations;
        public final int[] demands;

        public CuSPInstance(String fileName) throws FileNotFoundException {
            Scanner s = new Scanner(new File(fileName));

            String line = s.nextLine();
            nTasks = Integer.parseInt(line.substring(line.indexOf('=')+1, line.indexOf(';')));

            line = s.nextLine();
            capacity = Integer.parseInt(line.substring(line.indexOf('=')+1, line.indexOf(';')));

            line = s.nextLine();
            durations = Arrays.stream(line.substring(line.indexOf('[')+1, line.indexOf(']')).split(","))
                    .mapToInt(Integer::parseInt)
                    .toArray();

            line = s.nextLine();
            demands = Arrays.stream(line.substring(line.indexOf('[')+1, line.indexOf(']')).split(","))
                    .mapToInt(Integer::parseInt)
                    .toArray();
        }
    }
}
