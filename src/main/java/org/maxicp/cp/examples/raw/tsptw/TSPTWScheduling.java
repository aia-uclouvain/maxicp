/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.tsptw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.scheduling.NoOverlapWithPosition;
import org.maxicp.cp.engine.core.*;
import org.maxicp.cp.examples.utils.TSPTWInstance;
import org.maxicp.search.*;
import org.maxicp.util.Arrays;

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

        // position variables
        CPIntVar[] posOfVisits = CPFactory.makeIntVarArray(cp, instance.n, instance.n);
        CPIntVar[] visitsInPos = CPFactory.makeIntVarArray(cp, instance.n, instance.n);

        // enforce the permutation with transition times
        NoOverlapWithPosition perm = noOverlap(visits, posOfVisits, visitsInPos, instance.distMatrix);
        cp.post(perm);

        cp.post(eq(posOfVisits[0], 0)); // start at depot
        cp.post(startAt(visits[0], 0)); // at time 0
        cp.post(eq(posOfVisits[instance.n - 1], instance.n - 1)); // end at depot duplicate

        // transition cost: sum of transition times between consecutive visits
        int maxTransition = Arrays.max(instance.distMatrix);
        CPIntVar[] transitionTimes = makeIntVarArray(cp, instance.n - 1, 0, maxTransition);
        for (int i = 0; i < instance.n - 1; i++) {
            cp.post(eq(transitionTimes[i],
                    CPFactory.element(instance.distMatrix, visitsInPos[i], visitsInPos[i + 1])));
        }
        CPIntVar totTransition = CPFactory.sum(transitionTimes);

        Objective obj = cp.minimize(totTransition);

        // ===================== search =====================

        // choose the nodes in the order of the tour, trying first the nodes with the least positions
        DFSearch dfs = makeDfs(cp, heuristicNary(staticOrderVariableSelector(visitsInPos),
                i -> posOfVisits[i].size()));

        dfs.onSolution(() -> {
            System.out.println(java.util.Arrays.toString(visits));
            System.out.println("total distance: " + totTransition);
            // check solution
            int[] tour = java.util.Arrays.stream(visitsInPos).mapToInt(t -> t.min()).toArray();
            boolean ok = instance.checkSolution(tour, totTransition.min());
            assert(ok);
        });

        SearchStatistics stats = dfs.optimize(obj);

        System.out.println(stats);

    }

}

