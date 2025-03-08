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

import static org.maxicp.search.Searches.EMPTY;


/**
 * The N-Queens problem.
 * <a href="http://csplib.org/Problems/prob054/">CSPLib</a>.
 */
public class NQueens {
    public static void main(String[] args) {
        int n = 8;
        CPSolver cp = CPFactory.makeSolver(false);
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, n, n);


        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                cp.post(CPFactory.neq(q[i], q[j]));

                cp.post(CPFactory.neq(q[i], q[j], j - i));
                cp.post(CPFactory.neq(q[i], q[j], i - j));
                // alternative modeling using views
                // cp.post(notEqual(plus(q[i], j - i), q[j]));
                // cp.post(notEqual(minus(q[i], j - i), q[j]));

            }



        DFSearch search = CPFactory.makeDfs(cp, () -> {
            int idx = -1; // index of the first variable that is not fixed
            for (int k = 0; k < q.length; k++)
                if (q[k].size() > 1) {
                    idx = k;
                    break;
                }
            if (idx == -1)
                return EMPTY;
            else {
                CPIntVar qi = q[idx];
                int v = qi.min();
                Runnable left = () -> cp.post(CPFactory.eq(qi, v));
                Runnable right = () -> cp.post(CPFactory.neq(qi, v));
                return new Runnable[]{left, right};
            }
        });

        // a more compact first fail search using selectors is given next
/*
        DFSearch search = Factory.makeDfs(cp, () -> {
            IntVar qs = selectMin(q,
                    qi -> qi.size() > 1,
                    qi -> qi.size());
            if (qs == null) return EMPTY;
            else {
                int v = qs.min();
                return branch(() -> cp.post(Factory.equal(qs, v)),
                        () -> cp.post(Factory.notEqual(qs, v)));
            }
        });*/


        search.onSolution(() ->
                System.out.println("solution:" + Arrays.toString(q))
        );
        SearchStatistics stats = search.solve(statistics -> statistics.numberOfSolutions() == 1000);

        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);

    }
}
