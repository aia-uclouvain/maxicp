/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.search.Searches.firstFail;


public class IsEqualTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -4, 7);

        CPBoolVar b = CPFactory.isEq(x, -2);

        DFSearch search = CPFactory.makeDfs(cp, firstFail(x));

        SearchStatistics stats = search.solve();

        search.onSolution(() ->
                assertEquals(-2 == x.min(), b.isTrue())
        );

        assertEquals(12, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test2(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -4, 7);

        CPBoolVar b = CPFactory.isEq(x, -2);

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(b, 1));
        assertEquals(-2, x.min());
        cp.getStateManager().restoreState();

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(b, 0));
        assertFalse(x.contains(-2));
        cp.getStateManager().restoreState();
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test3(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -4, 7);
        cp.post(CPFactory.eq(x, -2));

        CPBoolVar b = CPFactory.makeBoolVar(cp);
        cp.post(new IsEqual(b, x, -2));
        assertTrue(b.isTrue());

        b = CPFactory.makeBoolVar(cp);
        cp.post(new IsEqual(b, x, -3));
        assertTrue(b.isFalse());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test4(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -4, 7);
        CPBoolVar b = CPFactory.makeBoolVar(cp);

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(b, 1));
        cp.post(new IsEqual(b, x, -2));
        assertEquals(-2, x.min());
        cp.getStateManager().restoreState();

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(b, 0));
        cp.post(new IsEqual(b, x, -2));
        assertFalse(x.contains(-2));
        cp.getStateManager().restoreState();
    }


}
