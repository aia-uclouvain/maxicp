/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import static org.junit.jupiter.api.Assertions.*;

public class LexLeTest extends CPSolverTest {

    private static boolean isLexLe(int[] a, int[] b, boolean strict) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] < b[i]) return true;
            if (a[i] > b[i]) return false;
        }
        return !strict;
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testLexLeAllSolutions(CPSolver cp) {
        int n = 3;
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, n, 3);
        CPIntVar[] y = CPFactory.makeIntVarArray(cp, n, 3);
        cp.post(new LexLe(x, y, false));

        CPIntVar[] all = new CPIntVar[2 * n];
        System.arraycopy(x, 0, all, 0, n);
        System.arraycopy(y, 0, all, n, n);

        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(all));
        dfs.onSolution(() -> {
            int[] xv = new int[n];
            int[] yv = new int[n];
            for (int i = 0; i < n; i++) {
                xv[i] = x[i].min();
                yv[i] = y[i].min();
            }
            assertTrue(isLexLe(xv, yv, false));
        });
        SearchStatistics stats = dfs.solve();
        // Total pairs of 3-tuples over {0,1,2}: 27 * 27 = 729; lex <= has 27*28/2 = 378
        assertEquals(378, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testLexLtAllSolutions(CPSolver cp) {
        int n = 3;
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, n, 3);
        CPIntVar[] y = CPFactory.makeIntVarArray(cp, n, 3);
        cp.post(new LexLe(x, y, true));

        CPIntVar[] all = new CPIntVar[2 * n];
        System.arraycopy(x, 0, all, 0, n);
        System.arraycopy(y, 0, all, n, n);

        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(all));
        dfs.onSolution(() -> {
            int[] xv = new int[n];
            int[] yv = new int[n];
            for (int i = 0; i < n; i++) {
                xv[i] = x[i].min();
                yv[i] = y[i].min();
            }
            assertTrue(isLexLe(xv, yv, true));
        });
        SearchStatistics stats = dfs.solve();
        // Total strict lex < pairs of 3-tuples over {0,1,2}: 27*26/2 = 351
        assertEquals(351, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testLexGacPruning(CPSolver cp) {
        // x0 in {2, 3}, x1 in {4, 5}
        // y0 in {1, 2}, y1 in {2, 3}
        // Suffix (x1, y1) is strictly violated (min(x1)=4 > max(y1)=3), so x0 < y0 is forced -> Inconsistent!
        CPIntVar[] x = new CPIntVar[]{
                CPFactory.makeIntVar(cp, 1, 3),
                CPFactory.makeIntVar(cp, 4, 5)
        };
        CPIntVar[] y = new CPIntVar[]{
                CPFactory.makeIntVar(cp, 1, 2),
                CPFactory.makeIntVar(cp, 2, 3)
        };
        cp.post(new LexLe(x, y, false));
        // Since x1 > y1 always, x0 < y0 must hold, so x0 <= 1 (x0 fixed to 1) and y0 >= 2 (y0 fixed to 2)
        assertEquals(1, x[0].max());
        assertEquals(2, y[0].min());
    }
}

