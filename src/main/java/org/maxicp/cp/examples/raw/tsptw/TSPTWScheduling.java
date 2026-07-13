/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.tsptw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.*;
import org.maxicp.cp.engine.constraints.scheduling.NoOverlapBinaryWithTransitionTime;
import org.maxicp.cp.engine.constraints.scheduling.Permutation;
import org.maxicp.cp.engine.core.*;
import org.maxicp.cp.examples.utils.TSPTWInstance;
import org.maxicp.modeling.IntVar;
import org.maxicp.search.*;
import org.maxicp.util.algo.DistanceMatrix;
import org.maxicp.util.io.InputReader;

import java.util.ArrayList;
import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

public class TSPTWScheduling {




    public static void main(String[] args) {

        TSPTWInstance instance = new TSPTWInstance("data/TSPTW/Dumas/n40w20.001.txt");

        CPSolver cp = makeSolver();

        // create visits as interval variables of length 0 with time windows
        CPIntervalVar [] visits = CPFactory.makeIntervalVarArray(cp, instance.n);
        for (int i = 0; i < instance.n; i++) {
            visits[i] = makeIntervalVar(cp, false, 0, 0); // 0 length interval
            visits[i].setStartMin(instance.earliest[i]);
            visits[i].setEndMax(instance.latest[i]);
        }
        // enforce the min distance constraints between visits
        Permutation perm = new Permutation(visits, instance.distMatrix);
        cp.post(perm);

        cp.post(eq(perm.posOfInterval[0],0)); // start at depot
        cp.post(startAt(visits[0],0 )); // at time 0
        cp.post(eq(perm.posOfInterval[instance.n-1],instance.n-1)); // end at depot duplicate

        // transition times and objective
        CPIntVar totTransition = perm.transitionCost(instance.distMatrix);

        Objective obj = cp.minimize(totTransition);

        // ===================== search =====================

        // choose the nodes in the order of the tour, trying first the nodes with the least positions
        DFSearch dfs = makeDfs(cp,heuristicNary(staticOrderVariableSelector(perm.intervalInPos),
                i -> perm.posOfInterval[i].size()));

        dfs.onSolution(() -> {;
            System.out.println(Arrays.toString(visits));
            System.out.println("total distance: " + totTransition);
            // check solution
            int[] tour = Arrays.stream(perm.intervalInPos).mapToInt(t -> t.min()).toArray();
            boolean ok = instance.checkSolution(tour, totTransition.min());
            assert(ok);
        });

        SearchStatistics stats = dfs.optimize(obj);

        System.out.println(stats);

    }

}

