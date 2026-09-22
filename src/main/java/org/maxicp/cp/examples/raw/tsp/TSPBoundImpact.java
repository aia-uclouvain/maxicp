/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.tsp;

import org.maxicp.cp.engine.constraints.Circuit;
import org.maxicp.cp.engine.constraints.CostAllDifferentDC;
import org.maxicp.cp.engine.constraints.Element1D;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.examples.utils.TSPInstance;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

/**
 * Traveling salesman problem.
 *
 * <a href="https://en.wikipedia.org/wiki/Travelling_salesman_problem">Wikipedia</a>.
 */
public class TSPBoundImpact {




    public static void main(String[] args) {

        TSPInstance instance = new TSPInstance("data/TSP/gr21.xml");
        int n = instance.n;
        int[][] distanceMatrix = instance.distanceMatrix;

        CPSolver cp = makeSolver(false);
        CPIntVar[] succ = makeIntVarArray(cp, n, n);
        CPIntVar[] distSucc = makeIntVarArray(n, i -> element(distanceMatrix[i], succ[i]));

        cp.post(circuit(succ));

        CPIntVar totalDist = sum(distSucc);
        Objective obj = cp.minimize(totalDist);

        // redundant constraint
        cp.post(new CostAllDifferentDC(succ,distanceMatrix,totalDist));

        DFSearch dfs = makeDfs(cp,
                heuristicBinary(minDomVariableSelector(succ),
                boundImpactValueSelector(totalDist)));

        dfs.onSolution(() ->
                System.out.println(totalDist)
        );

        SearchStatistics stats = dfs.optimize(obj);

        System.out.println(stats);


    }
}
