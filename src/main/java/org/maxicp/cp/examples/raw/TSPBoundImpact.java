/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.engine.constraints.Circuit;
import org.maxicp.cp.engine.constraints.Element1D;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.util.io.InputReader;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

/**
 * Traveling salesman problem.
 * <a href="https://en.wikipedia.org/wiki/Travelling_salesman_problem">Wikipedia</a>.
 */
public class TSPBoundImpact {


    /**
     * Fages, J. G., Prud’Homme, C. Making the first solution good! In 2017 IEEE 29th International Conference on Tools with Artificial Intelligence (ICTAI). IEEE.
     * @param x
     * @param obj
     * @return the value that if assigned to v induced the least augmentation of the objective obj
     */
    public static int boundImpactValueSelector(CPIntVar x, CPIntVar obj) {
        int val = x.min();
        int best = Integer.MAX_VALUE;
        for (int v = x.min(); v <= x.max(); v++) {
            if (x.contains(v)) {
                x.getSolver().getStateManager().saveState();
                try {
                    x.getSolver().post(eq(x,v));
                    if ((obj.min()) < best) {
                        val = v;
                        best = obj.min();
                    }
                } catch (InconsistencyException e) {

                }
                x.getSolver().getStateManager().restoreState();
            }
        }
        return val;
    }


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

        DFSearch dfs = makeDfs(cp, () -> {
            CPIntVar xs = selectMin(succ,
                    xi -> xi.size() > 1,
                    xi -> xi.size());
            if (xs == null)
                return EMPTY;
            else {
                int v = boundImpactValueSelector(xs,totalDist);// now the first solution should have objective 2561
                //int v = xs.min(); // the first solution should have objective 4722
                return branch(() -> cp.post(eq(xs, v)),
                        () -> cp.post(neq(xs, v)));
            }
        });

        dfs.onSolution(() ->
                System.out.println(totalDist)
        );

        SearchStatistics stats = dfs.optimize(obj);

        System.out.println(stats);


    }
}
