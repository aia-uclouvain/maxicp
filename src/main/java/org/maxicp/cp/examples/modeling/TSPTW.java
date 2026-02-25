/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.examples.utils.TSPTWInstance;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.TimeIt;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;

import static org.maxicp.modeling.Factory.*;

public class TSPTW {

    public static void main(String[] args) {

        TSPTWInstance instance = new TSPTWInstance("data/TSPTW/Dumas/n40w20.001.txt");
        int n = instance.n;

        ModelDispatcher baseModel = makeModelDispatcher();

        // decision vars: x[i] is the node visited at position i
        IntVar [] x = baseModel.intVarArray(n,n);

        // arrival[i] is the arrival time of node x[i]
        IntVar [] arrival = baseModel.intVarArray(n, instance.horizon);

        // transition[i] cost of the transition from x[i] to x[i+1]
        IntExpression [] transition = new IntExpression[n-1];

        // every node is visited exactly once (except last one)
        baseModel.add(allDifferent(x));

        // the time starts at 0 in the depot (i.e. node 0)
        baseModel.add(eq(arrival[0],0));
        baseModel.add(eq(x[0], 0));

        // the last node is the depot
        baseModel.add(eq(x[n-1], n-1));

        for (int i = 0; i < n; i++) {
            IntExpression earliest = get(instance.earliest, x[i]); // earliest = instance.earliest[x[i]]
            IntExpression latest = get(instance.latest, x[i]); // latest = instance.latest[x[i]]
            baseModel.add(le(arrival[i],latest)); // arrival[i] <= latest[i]
            baseModel.add(le(earliest,arrival[i])); // earliest[i] <= arrival[i]
        }

        for (int i = 0; i < n-1; i++) {
            transition[i] = get(instance.distMatrix,x[i],x[i+1]); // transition time between x[i] and x[i+1]
            IntExpression arrivalPlusTransition = sum(arrival[i], transition[i]); // arrivalPlusTransition[i] = arrival[i] + transition[i]
            baseModel.add(le(arrivalPlusTransition,arrival[i+1])); // arrivalPlusTransition[i] <= arrival[i+1]
        }

        IntExpression distance = sum(transition);

        long time = TimeIt.run(() -> {
            baseModel.runCP((cp) -> {
                DFSearch search = cp.dfSearch(Searches.firstFailBinary(x));
                search.onSolution(() -> {
                    System.out.println(Arrays.toString(x));
                    System.out.println("solution found distance:"+distance);
                });
                SearchStatistics stats = search.optimize(minimize(distance));
                System.out.println(stats);
            });
        });

        System.out.println("Time (s): " + (time/1000000000.));
    }

}


