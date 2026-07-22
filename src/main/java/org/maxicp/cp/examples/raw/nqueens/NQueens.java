/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.nqueens;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

import java.util.Arrays;


/**
 * The N-Queens problem.
 * <a href="http://csplib.org/Problems/prob054/">CSPLib</a>.
 */
public class NQueens {
    public static void main(String[] args) {
        int n = 8;

        CPSolver cp = CPFactory.makeSolver();
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, n, n);
        CPIntVar[] qL = CPFactory.makeIntVarArray(n, i -> minus(q[i],i));
        CPIntVar[] qR = CPFactory.makeIntVarArray(n, i -> plus(q[i],i));

        cp.post(allDifferent(q));
        cp.post(allDifferent(qL));
        cp.post(allDifferent(qR));


        // a more compact first fail search using selectors is given next
        DFSearch search = CPFactory.makeDfs(cp, () -> {
            CPIntVar qs = selectMin(q,
                    qi -> qi.size() > 1,
                    qi -> qi.size());
            if (qs == null) return EMPTY;
            else {
                int v = qs.min();
                return branch(() -> cp.post(eq(qs, v)),
                        () -> cp.post(neq(qs, v)));
            }
        });


        search.onSolution(() ->
                System.out.println("solution:" + Arrays.toString(q))
        );
        SearchStatistics stats = search.solve(statistics -> statistics.numberOfSolutions() == 1000);

        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);

    }
}
