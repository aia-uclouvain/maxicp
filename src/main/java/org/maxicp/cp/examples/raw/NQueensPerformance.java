/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

public class NQueensPerformance {
    public static void main(String[] args) {
        int n = 88;
        CPSolver cp = makeSolver();
        CPIntVar[] q = makeIntVarArray(cp, n, n);

        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                cp.post(neq(q[i], q[j]));
                cp.post(neq(plus(q[i], j - i), q[j]));
                cp.post(neq(minus(q[i], j - i), q[j]));
            }


        long t0 = System.currentTimeMillis();


        DFSearch dfs = makeDfs(cp, () -> {
            CPIntVar qs = selectMin(q,
                    qi -> qi.size() > 1,
                    qi -> qi.size());
            if (qs == null)
                return EMPTY;
            else {
                int v = qs.min();
                //return branch(() -> equal(qs, v), () -> notEqual(qs, v));
                return branch(() -> cp.post(eq(qs, v)), () -> cp.post(neq(qs, v)));
            }
        });

        dfs.onSolution(() ->
                System.out.println("solution:" + Arrays.toString(q))
        );


        SearchStatistics stats = dfs.solve(statistics -> {
            if ((statistics.numberOfNodes() / 2) % 10000 == 0) {
                //System.out.println("failures:"+statistics.nFailures);
                System.out.println("nodes:" + (statistics.numberOfNodes() / 2));
            }
            return statistics.numberOfSolutions() > 0 || statistics.numberOfNodes() >= 1000000;
        });


        System.out.println("time:" + (System.currentTimeMillis() - t0));
        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);

    }
}
