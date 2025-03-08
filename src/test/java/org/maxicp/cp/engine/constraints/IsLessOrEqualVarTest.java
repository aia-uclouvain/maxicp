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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.maxicp.search.Searches.firstFail;

public class IsLessOrEqualVarTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, 0, 5);
        CPIntVar y = CPFactory.makeIntVar(cp, 0, 5);

        CPBoolVar b = CPFactory.makeBoolVar(cp);

        cp.post(new IsLessOrEqualVar(b, x, y));

        DFSearch search = CPFactory.makeDfs(cp, firstFail(x, y));

        SearchStatistics stats = search.solve();

        search.onSolution(() ->
                assertTrue(x.min() <= y.min() && b.isTrue() || x.min() > y.min() && b.isFalse())
        );

        assertEquals(36, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test2(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -8, 7);
        CPIntVar y = CPFactory.makeIntVar(cp, -4, 3);

        CPBoolVar b = CPFactory.makeBoolVar(cp);

        cp.post(new IsLessOrEqualVar(b, x, y));

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(b, 1));
        assertEquals(3, x.max());
        cp.getStateManager().restoreState();

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(b, 0));
        assertEquals(-3, x.min());
        cp.getStateManager().restoreState();
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test3(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -4, 7);
        CPIntVar y = CPFactory.makeIntVar(cp, 0, 7);
        cp.post(CPFactory.eq(x, -2));

        CPBoolVar b = CPFactory.makeBoolVar(cp);
        cp.post(new IsLessOrEqualVar(b, x, y));
        assertTrue(b.isTrue());
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


}
