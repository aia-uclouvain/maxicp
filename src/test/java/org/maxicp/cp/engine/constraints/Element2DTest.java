/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;
import org.maxicp.search.Searches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.search.Searches.firstFailBinary;


public class Element2DTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element2dTest1(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -2, 40);
        CPIntVar y = CPFactory.makeIntVar(cp, -3, 10);
        CPIntVar z = CPFactory.makeIntVar(cp, 2, 40);

        int[][] T = new int[][]{
                {9, 8, 7, 5, 6},
                {9, 1, 5, 2, 8},
                {8, 3, 1, 4, 9},
                {9, 1, 2, 8, 6},
        };

        cp.post(new Element2D(T, x, y, z));

        assertEquals(0, x.min());
        assertEquals(0, y.min());
        assertEquals(3, x.max());
        assertEquals(4, y.max());
        assertEquals(2, z.min());
        assertEquals(9, z.max());

        z.removeAbove(7);
        cp.fixPoint();

        assertEquals(1, y.min());

        x.remove(0);
        cp.fixPoint();

        assertEquals(6, z.max());
        assertEquals(3, x.max());

        y.remove(4);
        cp.fixPoint();

        assertEquals(5, z.max());
        assertEquals(2, z.min());

        y.remove(2);
        cp.fixPoint();

        assertEquals(4, z.max());
        assertEquals(2, z.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element2dTest2(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -2, 40);
        CPIntVar y = CPFactory.makeIntVar(cp, -3, 10);
        CPIntVar z = CPFactory.makeIntVar(cp, -20, 40);

        int[][] T = new int[][]{
                {9, 8, 7, 5, 6},
                {9, 1, 5, 2, 8},
                {8, 3, 1, 4, 9},
                {9, 1, 2, 8, 6},
        };

        cp.post(new Element2D(T, x, y, z));

        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x, y, z));
        dfs.onSolution(() ->
                assertEquals(T[x.min()][y.min()], z.min())
        );
        SearchStatistics stats = dfs.solve();

        assertEquals(20, stats.numberOfSolutions());

    }

}
