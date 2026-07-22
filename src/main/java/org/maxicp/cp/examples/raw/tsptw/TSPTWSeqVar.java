/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.tsptw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.examples.utils.TSPTWInstance;
import org.maxicp.modeling.Factory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.algo.DistanceMatrix;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * The Traveling Salesman Problem with Time Windows (TSPTW) is a variation of
 * the classic Traveling Salesman Problem (TSP) in which each city must
 * be visited within a specific time interval.
 * More exactly, each city i is associated with a time window [a_i, b_i],
 * meaning the salesman must arrive at that city no earlier than a_i and no later than b_i.
 * If the salesman arrives early, they may wait; arriving after b_i makes the solution infeasible.
 * The objective is to minimize the total travel time.
 *
 * This model makes use of the successor variables. See:
 * Augustin Delcluse, Pierre Schaus, Pascal Van Hentenryck
 * Sequence Variables: A Constraint Programming Computational Domain for Routing and Sequencing
 * https://pschaus.github.io/assets/publi/seqvars.pdf
 *
 * Instances from: https://lopez-ibanez.eu/tsptw-instances
 */
public class TSPTWSeqVar {
    public static void main(String[] args) {

        // In the instance representation, the depot is duplicated at the end of the instance.
        // So we don't seek for a tour but for a path from the depot to its duplicate at the end
        TSPTWInstance instance = new TSPTWInstance("data/TSPTW/Dumas/n40w20.001.txt");

        CPSolver cp = makeSolver();

        // Sequence variable representing the path from depot to its duplicate
        CPSeqVar tour = CPFactory.makeSeqVar(cp, instance.n, 0, instance.n-1);

        // All nodes must be visited (by default they are optional in seq var)
        for (int i = 0; i < instance.n; i++) {
            tour.require(i);
        }

        // Time window vars that represent when nodes are visited
        CPIntVar[] timeWindows = makeIntVarArray(cp, instance.n, instance.horizon);
        cp.post(eq(timeWindows[0],0)); // start at time 0 in depot
        for (int i = 1; i < instance.n; i++) {
            timeWindows[i].removeAbove(instance.latest[i]);
            timeWindows[i].removeBelow(instance.earliest[i]);
        }
        // Link time windows and distances in the tour
        cp.post(new TransitionTimes(tour, timeWindows, instance.distMatrix));

        // Total distance of the tour
        CPIntVar totTransition = makeIntVar(cp, 0 , 100000);
        cp.post(new Distance(tour,instance.distMatrix, totTransition));

        // Objective: minimize total distance
        Objective obj = cp.minimize(totTransition);

        // ===================== search =====================

        int[] nodes = new int[instance.n];
        DFSearch dfs = makeDfs(cp,
                // each decision in the search tree will minimize the detour of adding a new node to the path
                () -> {
                    if (tour.isFixed())
                        return EMPTY;
                    // Select node with minimum number of insertions points
                    int nUnfixed = tour.fillNode(nodes, INSERTABLE);
                    int node = Searches.selectMin(nodes, nUnfixed, i -> true, tour::nInsert).getAsInt();
                    // Get the insertion of the node with the smallest detour cost
                    int nInsert = tour.fillInsert(node, nodes);
                    int bestPred = Searches.selectMin(nodes, nInsert, pred -> true,
                            pred -> {
                                int succ = tour.memberAfter(node);
                                return instance.distMatrix[pred][node] +
                                        instance.distMatrix[node][succ] -
                                        instance.distMatrix[pred][succ];
                            }).getAsInt();
                    // Successor of the insertion
                    int succ = tour.memberAfter(bestPred);
                    // Either use the insertion to form bestPred -> node -> succ, or remove the detour
                    return branch(() -> cp.getModelProxy().add(Factory.insert(tour, bestPred, node)),
                            () -> cp.getModelProxy().add(Factory.notBetween(tour, bestPred, node, succ)));
                }
        );

        dfs.onSolution(() -> {;
            System.out.println("tour: " + tour);
            System.out.println("total distance: " + totTransition);
        });

        SearchStatistics stats = dfs.optimize(obj);

        System.out.println(stats);

    }

}

