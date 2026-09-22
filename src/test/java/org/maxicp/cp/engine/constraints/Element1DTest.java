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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.maxicp.search.Searches.firstFailBinary;

public class Element1DTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dTest1(CPSolver cp) {
        CPIntVar y = CPFactory.makeIntVar(cp, -3, 10);
        CPIntVar z = CPFactory.makeIntVar(cp, 2, 40);

        int[] T = new int[]{9, 8, 7, 5, 6};

        cp.post(new Element1D(T, y, z));

        assertEquals(0, y.min());
        assertEquals(4, y.max());


        assertEquals(5, z.min());
        assertEquals(9, z.max());

        z.removeAbove(7);
        cp.fixPoint();

        assertEquals(2, y.min());

        y.remove(3);
        cp.fixPoint();

        assertEquals(7, z.max());
        assertEquals(6, z.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dTest2(CPSolver cp) {
        CPIntVar y = CPFactory.makeIntVar(cp, -3, 10);
        CPIntVar z = CPFactory.makeIntVar(cp, -20, 40);

        int[] T = new int[]{9, 8, 7, 5, 6};

        cp.post(new Element1D(T, y, z));

        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(y, z));
        dfs.onSolution(() ->
                assertEquals(T[y.min()], z.min())
        );
        SearchStatistics stats = dfs.solve();

        assertEquals(5, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dTest3(CPSolver cp) {
        CPIntVar y = CPFactory.makeIntVar(cp, 0, 4);
        CPIntVar z = CPFactory.makeIntVar(cp, 5, 9);


        int[] T = new int[]{9, 8, 7, 5, 6};

        cp.post(new Element1D(T, y, z));

        y.remove(3); //T[4]=5
        y.remove(0); //T[0]=9

        cp.fixPoint();

        assertEquals(6, z.min());
        assertEquals(8, z.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dTest4(CPSolver cp) {
        CPIntVar y = CPFactory.makeIntVar(cp, 0, 4);
        CPIntVar z = CPFactory.makeIntVar(cp, 5, 9);


        int[] T = new int[]{9, 8, 7, 5, 6};

        cp.post(new Element1D(T, y, z));

        z.remove(9); //new max is 8
        z.remove(5); //new min is 6
        cp.fixPoint();

        assertFalse(y.contains(0));
        assertFalse(y.contains(3));
    }

}
