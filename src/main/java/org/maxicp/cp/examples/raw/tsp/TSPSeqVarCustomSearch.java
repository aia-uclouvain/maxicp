/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.tsp;

import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.examples.utils.TSPInstance;
import org.maxicp.modeling.Factory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.algo.DistanceMatrix;

import java.util.List;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * Traveling salesman problem.
 * <a href="https://en.wikipedia.org/wiki/Travelling_salesman_problem">Wikipedia</a>.
 */
public class TSPSeqVarCustomSearch {

    public static void main(String[] args) {

        TSPInstance instance = new TSPInstance("data/TSP/gr21.xml");

        // ===================== read & preprocessing =====================

        int n = instance.n;
        // duplicate the 0 in the distance matrix, as seqvar need a proper start and end node
        int[][] distance = DistanceMatrix.extendMatrixAtEnd(instance.distanceMatrix, List.of(0));

        // ===================== decision variables =====================

        CPSolver cp = makeSolver();
        // route for the traveler
        CPSeqVar tour = makeSeqVar(cp, n + 1, 0, n);
        // distance traveled. This is the objective to minimize
        CPIntVar totLength = makeIntVar(cp, 0, 10000);

        // ===================== constraints =====================

        // all nodes must be visited in a tsp
        for (int node = 0; node < n + 1; node++) {
            tour.require(node);
        }
        // capture the distance traveled according to the distance matrix
        cp.post(new Distance(tour, distance, totLength));
        // objective consists in minimizing the traveled distance
        Objective obj = cp.minimize(totLength);

        // ===================== search =====================

        int[] nodes = new int[n];
        DFSearch dfs = makeDfs(cp,
                // each decision in the search tree will minimize the detour of adding a new node to the path
                () -> {
                    if (tour.isFixed())
                        return EMPTY;
                    // select node with minimum number of insertions points
                    int nUnfixed = tour.fillNode(nodes, INSERTABLE);
                    int node = Searches.selectMin(nodes, nUnfixed, i -> true, tour::nInsert).getAsInt();
                    // get the insertion of the node with the smallest detour cost
                    int nInsert = tour.fillInsert(node, nodes);
                    int bestPred = Searches.selectMin(nodes, nInsert, pred -> true,
                            pred -> {
                                int succ = tour.memberAfter(node);
                                return distance[pred][node] + distance[node][succ] - distance[pred][succ];
                            }).getAsInt();
                    // successor of the insertion
                    int succ = tour.memberAfter(bestPred);
                    // either use the insertion to form bestPred -> node -> succ, or remove the detour
                    return branch(() -> cp.getModelProxy().add(Factory.insert(tour, bestPred, node)),
                            () -> cp.getModelProxy().add(Factory.notBetween(tour, bestPred, node, succ)));
                }
        );

        // ===================== solve the problem =====================

        long init = System.currentTimeMillis();
        dfs.onSolution(() -> {
            double elapsedSeconds = (double) (System.currentTimeMillis() - init) / 1000.0;
            System.out.printf("elapsed: %.3f%n", elapsedSeconds);
            System.out.println(totLength);
            System.out.println(tour);
            System.out.println("-------");
        });

        SearchStatistics stats = dfs.optimize(obj);
        double elapsedSeconds = (double) (System.currentTimeMillis() - init) / 1000.0;
        System.out.printf("elapsed - total: %.3f%n", elapsedSeconds);
        System.out.println(stats);
    }

}

