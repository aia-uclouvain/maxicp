/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.tsp;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.RelaxedSequence;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.examples.utils.TSPInstance;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.algo.DistanceMatrix;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

/**
 * Traveling salesman problem.
 * <a href="https://en.wikipedia.org/wiki/Travelling_salesman_problem">Wikipedia</a>.
 */
public class TSPSeqVarLNS {



    public static void main(String[] args) {

        TSPInstance instance = new TSPInstance("data/TSP/instance_30_0.xml");
        int n = instance.n;
        // duplicate the 0 (start node) in the distance matrix, as seqvar needs a proper start and end node
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

        // ================ best sol (LNS) ==================

        // best so far tour
        int [] bestTour = IntStream.range(0,n+1).toArray();

        // ===================== search =====================

        int[] nodes = new int[n];
        DFSearch dfs = makeDfs(cp, firstFailBinary(tour));

        // ===================== solve the problem =====================

        long init = System.currentTimeMillis();
        dfs.onSolution(() -> {
            // update best solution
            tour.fillNode(bestTour, SeqStatus.MEMBER_ORDERED);
            System.out.println(totLength);
        });

        // LNS loop
        Random random = new Random(42);
        int percentageOfFixed = 90; // percentage of fixed nodes
        for (int iter = 0; iter < 100; iter++) {
            System.out.println(String.format("---restart %d ----", iter));
            dfs.optimizeSubjectTo(obj, s -> false, () -> {
                Set<Integer> relaxed = randomSubset(random, 0, n, 5);
                // relax current solution
                cp.post(new RelaxedSequence(tour, bestTour, relaxed));
            });
        }
    }

    /**
     * Random selection of a set of cardinality size on the range [from..to-1]
     */
    public static Set<Integer> randomSubset(Random rand, int from, int to, int size) {
        if (size > to-from)
            throw new IllegalArgumentException(String.format("size %d > %d", size, to-from));
        Set<Integer> res = new HashSet<>();
        while (res.size() < size) {
            res.add(rand.nextInt(to-from) + from);
        }
        return res;
    }

}

