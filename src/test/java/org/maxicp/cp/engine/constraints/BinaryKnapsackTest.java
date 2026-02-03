/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.search.Searches.branch;

class BinaryKnapsackTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest(CPSolver cp) {
        CPBoolVar[] x = new CPBoolVar[3];
        for (int i = 0; i < 3; i++) {
            x[i] = CPFactory.makeBoolVar(cp);
        }
        int[] w = new int[]{1, 2, 4};
        CPIntVar load = CPFactory.makeIntVar(cp, 0, 3);
        cp.post(new BinaryKnapsack(x, w, load));

        assertFalse(x[0].isFixed());
        assertFalse(x[1].isFixed());
        assertTrue(x[2].isFalse());

        x[0].fix(true);
        x[1].fix(true);
        cp.fixPoint();
        assertEquals(3,load.min());
    }

    // decompose the constraint with a sum and view multiplication
    @ParameterizedTest
    @MethodSource("getSolver")
    public void randomTest(CPSolver cp) {
        int n = 10;
        Random r = new Random(0);
        for (int iter = 0; iter < 10; iter++) {
            CPBoolVar[] x = new CPBoolVar[n];
            int[] w = new int[n];
            CPIntVar[] loadExpr = new CPIntVar[n];
            for (int i = 0; i < n; i++) {
                x[i] = CPFactory.makeBoolVar(cp);
                w[i] = r.nextInt(10) + 1;
                loadExpr[i] = CPFactory.mul(x[i], w[i]);
            }
            CPIntVar load = CPFactory.makeIntVar(cp, 0, n);
            cp.getStateManager().saveState();
            cp.post(new BinaryKnapsack(x, w, load));
            DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFail(x));
            long t0 = System.currentTimeMillis();
            SearchStatistics stat1 = dfs.solve();
            cp.getStateManager().restoreState();
            // using decomposition only
            cp.post(CPFactory.sum(loadExpr, load));
            long t1 = System.currentTimeMillis();
            SearchStatistics stat2 = dfs.solve();
            long t2 = System.currentTimeMillis();
            assertEquals(stat1.numberOfSolutions(), stat2.numberOfSolutions());
        }
    }

}