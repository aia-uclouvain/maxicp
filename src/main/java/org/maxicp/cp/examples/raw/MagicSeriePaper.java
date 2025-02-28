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

import java.util.Arrays;

import static org.maxicp.search.Searches.EMPTY;

/**
 * The Magic Series problem.
 * <a href="http://csplib.org/Problems/prob019/">CSPLib</a>.
 */
public class MagicSeriePaper {
    public static void main(String[] args) {
        int n = 8;
        CPSolver cp = CPFactory.makeSolver(false);
        CPIntVar[] s = CPFactory.makeIntVarArray(cp, n, n);

        for (int i = 0; i < n; i++) {
            final int fi = i;
            cp.post(CPFactory.sum(CPFactory.makeIntVarArray(n, j -> CPFactory.isEqual(s[j], fi)), s[i]));
        }
        cp.post(CPFactory.sum(s, n));
        cp.post(CPFactory.sum(CPFactory.makeIntVarArray(n, i -> CPFactory.mul(s[i], i)), n));

        DFSearch dfs = CPFactory.makeDfs(cp, () -> {
            int idx = -1; // index of the first variable that is not fixed
            for (int k = 0; k < s.length; k++)
                if (s[k].size() > 1) {
                    idx = k;
                    break;
                }
            if (idx == -1)
                return EMPTY;
            else {
                CPIntVar si = s[idx];
                int v = si.min();
                Runnable left = () -> cp.post(CPFactory.equal(si, v));
                Runnable right = () -> cp.post(CPFactory.notEqual(si, v));
                return new Runnable[]{left, right};
            }                
        });

        dfs.onSolution(() ->
                System.out.println("solution:" + Arrays.toString(s))
        );
        dfs.solve();
    }
}
