/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.engine.constraints.AllDifferentDC;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.examples.utils.TSPTWInstance;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.TimeIt;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;

public class TSPTW {
    public static void main(String[] args) {

        TSPTWInstance instance = new TSPTWInstance("data/TSPTW/Dumas/n40w20.001.txt");

        CPSolver cp = makeSolver();

        CPIntVar [] arrival = makeIntVarArray(cp,instance.n+1, instance.horizon);

        // x[i] is the node visited at position i
        CPIntVar [] x = makeIntVarArray(cp,instance.n+1, instance.n);

        CPIntVar [] transition = new CPIntVar[instance.n];

        // every node is visited exactly once (except last one)
        cp.post(allDifferent(Arrays.copyOf(x,instance.n)));
        cp.post(new AllDifferentDC(Arrays.copyOf(x,instance.n)));

        // the time starts at 0 in the depot (i.e. node 0)
        cp.post(eq(arrival[0],0));
        cp.post(eq(x[0], 0));

        // the last node is the depot
        cp.post(eq(x[instance.n], 0));


        for (int i = 0; i < instance.n+1; i++) {
            CPIntVar earliest = element(instance.earliest,x[i]); // earliest[i] = instance.earliest[x[i]]
            CPIntVar latest = element(instance.latest, x[i]); // latest[i] = instance.latest[x[i]]
            cp.post(le(arrival[i],latest)); // arrival[i] <= latest[i]
            cp.post(le(earliest,arrival[i])); // earliest[i] <= arrival[i]
        }

        for (int i = 0; i < instance.n; i++) {
            transition[i] = element(instance.distMatrix,x[i],x[i+1]); // transition time between x[i] and x[i+1]
            CPIntVar arrivalPlusTransition = sum(arrival[i], transition[i]); // arrivalPlusTransition[i] = arrival[i] + transition[i]
            cp.post(le(arrivalPlusTransition,arrival[i+1])); // arrivalPlusTransition[i] <= arrival[i+1]
        }

        CPIntVar distance = sum(transition);


        DFSearch search = makeDfs(cp, Searches.firstFail(x));

        search.onSolution(() -> {
            System.out.println(Arrays.toString(x));
            System.out.println("solution found distance:"+distance);
        });

        long time = TimeIt.run(() -> {
            SearchStatistics stats = search.optimize(cp.minimize(distance));
            System.out.println(stats);
        });

        System.out.println("Time (s): " + (time/1000000000.));



    }


}


