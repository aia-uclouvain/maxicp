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
import org.maxicp.util.exception.InconsistencyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.firstFail;

class AtLeastNValueFWCTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAtLeast1(CPSolver cp) {
        int n = 4;
        CPIntVar[] Xs = CPFactory.makeIntVarArray(cp, n, 0, 3);
        CPIntVar y = CPFactory.makeIntVar(cp, 2, 2);
        cp.post(new AtLeastNValueFWC(Xs, y));

        DFSearch dfs = CPFactory.makeDfs(cp, firstFail(Xs));


        dfs.onSolution(() -> {
            int[] values = new int[4];
            for (CPIntVar X : Xs) {
                values[X.min()] = 1;
            }
            assertTrue(values[0] + values[1] + values[2] + values[3] == y.min());
        });

        SearchStatistics stats = dfs.solve();
        assertTrue(stats.numberOfSolutions() == 84);
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAtLeast2(CPSolver cp) {

        int n = 4;
        CPIntVar[] Xs = CPFactory.makeIntVarArray(cp, n, 0, 3);
        CPIntVar y = CPFactory.makeIntVar(cp, 2, 5);

        cp.post(CPFactory.eq(Xs[0],0));
        cp.post(CPFactory.eq(Xs[1],0));
        cp.post(CPFactory.eq(Xs[2],0));
        cp.post(new AtLeastNValueFWC(Xs, y));

        assertFalse(Xs[3].contains(0));
        assertEquals(3, Xs[3].size());
        assertEquals(2, y.max());


    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAtLeast3(CPSolver cp) {

        int n = 4;
        CPIntVar[] Xs = CPFactory.makeIntVarArray(cp, n, 1, 4);
        CPIntVar y = CPFactory.makeIntVar(cp, 3, 4);

        cp.post(CPFactory.eq(Xs[0],1));
        cp.post(CPFactory.eq(Xs[1],1));
        cp.post(CPFactory.eq(Xs[2],2));
        cp.post(new AtLeastNValueFWC(Xs, y));


        assertEquals(2, Xs[3].size());
        assertEquals(3, y.max());
        assertEquals(3, y.min());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAtLeast4(CPSolver cp) {

        CPIntVar[] x = makeIntVarArray(cp, 5, 5);
        CPIntVar y = CPFactory.makeIntVar(cp, 5, 6);

        cp.post(new AtLeastNValueFWC(x, y));

        cp.post(eq(x[2], 3));
        try {
            cp.post(eq(x[1], 3));
            fail();
        } catch (InconsistencyException e) {

        }

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAtLeast5(CPSolver cp) {

        CPIntVar[] x = makeIntVarArray(cp, 5, 5);
        CPIntVar y = makeIntVar(cp, 5, 6);
        cp.post(new AtLeastNValueFWC(x, y));

        assertEquals(5, y.max());

        cp.post(eq(x[2], 3));
        try {
            cp.post(eq(x[1], 3));
            fail();
        } catch (InconsistencyException e) {

        }

    }

}