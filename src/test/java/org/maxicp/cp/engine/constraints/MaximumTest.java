/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.search.Searches.firstFail;

public class MaximumTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void maximumTest1(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 10);
        CPIntVar y = CPFactory.makeIntVar(cp, -5, 20);
        cp.post(new Maximum(x, y));

        assertEquals(9, y.max());
        assertEquals(0, y.min());

        y.removeAbove(8);
        cp.fixPoint();

        assertEquals(8, x[0].max());
        assertEquals(8, x[1].max());
        assertEquals(8, x[2].max());

        y.removeBelow(5);
        x[0].removeAbove(2);
        x[1].removeBelow(6);
        x[2].removeBelow(6);
        cp.fixPoint();

        assertEquals(8, y.max());
        assertEquals(6, y.min());

        y.removeBelow(7);
        x[1].removeAbove(6);
        cp.fixPoint();
        // x0 = 0..2
        // x1 = 6
        // x2 = 6..8
        // y = 7..8
        assertEquals(7, x[2].min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void maximumTest2(CPSolver cp) {
        CPIntVar x1 = CPFactory.makeIntVar(cp, 0, 0);
        CPIntVar x2 = CPFactory.makeIntVar(cp, 1, 1);
        CPIntVar x3 = CPFactory.makeIntVar(cp, 2, 2);
        CPIntVar y = CPFactory.maximum(x1, x2, x3);

        assertEquals(2, y.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void maximumTest3(CPSolver cp) {
        CPIntVar x1 = CPFactory.makeIntVar(cp, 0, 10);
        CPIntVar x2 = CPFactory.makeIntVar(cp, 0, 10);
        CPIntVar x3 = CPFactory.makeIntVar(cp, -5, 50);
        CPIntVar y = CPFactory.maximum(x1, x2, x3);

        y.removeAbove(5);
        cp.fixPoint();

        assertEquals(5, x1.max());
        assertEquals(5, x2.max());
        assertEquals(5, x3.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void maximumTest4(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 4, 5);
        CPIntVar y = CPFactory.makeIntVar(cp, -5, 20);

        CPIntVar[] allIntVars = new CPIntVar[x.length+1];
        System.arraycopy(x, 0, allIntVars, 0, x.length);
        allIntVars[x.length] = y;

        DFSearch dfs = CPFactory.makeDfs(cp, firstFail(allIntVars));

        cp.post(new Maximum(x, y));
        // 5*5*5*5 // 625

        dfs.onSolution(() -> {
            int max = Arrays.stream(x).mapToInt(CPIntVar::max).max().getAsInt();
            assertEquals(y.min(), max);
            assertEquals(y.max(), max);
        });

        SearchStatistics stats = dfs.solve();

        assertEquals(625, stats.numberOfSolutions());
    }
}
