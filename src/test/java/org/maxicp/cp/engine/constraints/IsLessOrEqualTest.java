/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;
import org.maxicp.search.Searches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.maxicp.search.Searches.firstFailBinary;

public class IsLessOrEqualTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -4, 7);

        CPBoolVar b = CPFactory.makeBoolVar(cp);

        cp.post(new IsLessOrEqual(b, x, 3));

        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));

        search.onSolution(() ->
                assertTrue(x.min() <= 3 && b.isTrue() || x.min() > 3 && b.isFalse())
        );

        SearchStatistics stats = search.solve();

        assertEquals(12, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test2(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -4, 7);

        CPBoolVar b = CPFactory.makeBoolVar(cp);

        cp.post(new IsLessOrEqual(b, x, -2));

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(b, 1));
        assertEquals(-2, x.max());
        cp.getStateManager().restoreState();

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(b, 0));
        assertEquals(-1, x.min());
        cp.getStateManager().restoreState();
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test3(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -4, 7);
        cp.post(CPFactory.eq(x, -2));

        CPBoolVar b = CPFactory.makeBoolVar(cp);
        cp.post(new IsLessOrEqual(b, x, -2));
        assertTrue(b.isTrue());

        b = CPFactory.makeBoolVar(cp);
        cp.post(new IsLessOrEqual(b, x, -3));
        assertTrue(b.isFalse());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test4(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -4, 7);
        CPBoolVar b = CPFactory.makeBoolVar(cp);

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(b, 1));
        cp.post(new IsLessOrEqual(b, x, -2));
        assertEquals(-2, x.max());
        cp.getStateManager().restoreState();

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(b, 0));
        cp.post(new IsLessOrEqual(b, x, -2));
        assertEquals(-1, x.min());
        cp.getStateManager().restoreState();
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test5(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -5, 10);
        CPBoolVar b = CPFactory.makeBoolVar(cp);

        cp.getStateManager().saveState();
        cp.post(new IsLessOrEqual(b, x, -6));
        assertTrue(b.isFixed());
        assertTrue(b.isFalse());
        cp.getStateManager().restoreState();

        cp.getStateManager().saveState();
        cp.post(new IsLessOrEqual(b, x, 11));
        assertTrue(b.isFixed());
        assertTrue(b.isTrue());
        cp.getStateManager().restoreState();
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test6(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -5, -3);
        CPBoolVar b = CPFactory.makeBoolVar(cp);

        cp.getStateManager().saveState();
        cp.post(new IsLessOrEqual(b, x, -3));
        assertTrue(b.isTrue());
        cp.getStateManager().restoreState();
    }

}
