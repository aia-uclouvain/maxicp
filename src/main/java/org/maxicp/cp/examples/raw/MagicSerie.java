/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

/**
 * The Magic Series problem.
 * <a href="http://csplib.org/Problems/prob019/">CSPLib</a>.
 */
public class MagicSerie {

    public static CPIntVar[] flatten(CPIntVar[][] x) {
        return Arrays.stream(x).flatMap(Arrays::stream).toArray(CPIntVar[]::new);
    }

    public static void main(String[] args) {

        int n = 300;
        CPSolver cp = makeSolver(false);

        CPIntVar[] s = makeIntVarArray(cp, n, n);

        for (int i = 0; i < n; i++) {
            final int fi = i;
            cp.post(sum(CPFactory.makeIntVarArray(n, j -> isEq(s[j], fi)), s[i]));
        }
        cp.post(sum(s, n));
        cp.post(sum(CPFactory.makeIntVarArray(n, i -> mul(s[i], i)), n));
        cp.post(sum(makeIntVarArray(n - 1, i -> mul(s[i], i - 1)), 0));

        long t0 = System.currentTimeMillis();
        DFSearch dfs = makeDfs(cp, () -> {
            CPIntVar sv = selectMin(s,
                    si -> si.size() > 1,
                    si -> -si.size());
            if (sv == null) return EMPTY;
            else {
                int v = sv.min();
                return branch(() -> cp.post(eq(sv, v)),
                        () -> cp.post(neq(sv, v)));
            }
        });

        dfs.onSolution(() ->
                System.out.println("solution:" + Arrays.toString(s))
        );

        SearchStatistics stats = dfs.solve();

        long t1 = System.currentTimeMillis();

        System.out.println(t1 - t0);

        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);

    }
}
