/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.engine.constraints.Circuit;
import org.maxicp.cp.engine.constraints.CostAllDifferentDC;
import org.maxicp.cp.engine.constraints.Element1D;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

/**
 * Traveling salesman problem.
 * <a href="https://en.wikipedia.org/wiki/Travelling_salesman_problem">Wikipedia</a>.
 */
public class TSP {



    public static void main(String[] args) {

        // instance gr17 https://people.sc.fsu.edu/~jburkardt/datasets/tsp/gr17_d.txt
        InputReader reader = new InputReader("data/TSP/tsp.txt");

        int n = reader.getInt();

        int[][] distanceMatrix = reader.getIntMatrix(n, n);

        CPSolver cp = makeSolver(false);
        CPIntVar[] succ = makeIntVarArray(cp, n, n);
        CPIntVar[] distSucc = makeIntVarArray(cp, n, 1000);

        cp.post(new Circuit(succ));
        for (int i = 0; i < n; i++) {
            cp.post(new Element1D(distanceMatrix[i], succ[i], distSucc[i]));
        }

        CPIntVar totalDist = sum(distSucc);
        Objective obj = cp.minimize(totalDist);

        // redundant constraint
        cp.post(new CostAllDifferentDC(succ,distanceMatrix,totalDist));

        DFSearch dfs = makeDfs(cp, staticOrder(succ));

        dfs.onSolution(() -> {
            System.out.println(totalDist);
        });

        long t0 = System.currentTimeMillis();
        SearchStatistics stats = dfs.optimize(obj);
        System.out.println(stats);
        System.out.println("Time: " + (System.currentTimeMillis() - t0) + "ms");

    }
}
