/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
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
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.util.exception.IntOverFlowException;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

public class SumTest extends CPSolverTest {


    private static CPIntVar makeIVar(CPSolver cp, Integer... values) {
        return CPFactory.makeIntVar(cp, new HashSet<>(Arrays.asList(values)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum1(CPSolver cp) {
        CPIntVar y = CPFactory.makeIntVar(cp, -100, 100);
        CPIntVar[] x = new CPIntVar[]{
                CPFactory.makeIntVar(cp, 0, 5),
                CPFactory.makeIntVar(cp, 1, 5),
                CPFactory.makeIntVar(cp, 0, 5)
        };
        cp.post(new Sum(x, y));

        assertEquals(1, y.min());
        assertEquals(15, y.max());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum2(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{
                CPFactory.makeIntVar(cp, -5, 5),
                CPFactory.makeIntVar(cp, 1, 2),
                CPFactory.makeIntVar(cp, 0, 1)
        };
        CPIntVar y = CPFactory.makeIntVar(cp, 0, 100);
        cp.post(new Sum(x, y));

        assertEquals(-3, x[0].min());
        assertEquals(0, y.min());
        assertEquals(8, y.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum3(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{
                CPFactory.makeIntVar(cp, -5, 5),
                CPFactory.makeIntVar(cp, 1, 2),
                CPFactory.makeIntVar(cp, 0, 1)
        };
        CPIntVar y = CPFactory.makeIntVar(cp, 5, 5);
        cp.post(new Sum(x, y));

        x[0].removeBelow(1);
        // 1-5 + 1-2 + 0-1 = 5
        x[1].fix(1);
        // 1-5 + 1 + 0-1 = 5
        cp.fixPoint();

        assertEquals(4, x[0].max());
        assertEquals(3, x[0].min());
        assertEquals(1, x[2].max());
        assertEquals(0, x[2].min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum4(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{
                CPFactory.makeIntVar(cp, 0, 5),
                CPFactory.makeIntVar(cp, 0, 2),
                CPFactory.makeIntVar(cp, 0, 1)
        };
        cp.post(new Sum(x, 0));

        assertEquals(0, x[0].max());
        assertEquals(0, x[1].max());
        assertEquals(0, x[2].max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum5(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{
                CPFactory.makeIntVar(cp, -5, 0),
                CPFactory.makeIntVar(cp, -5, 0),
                CPFactory.makeIntVar(cp, -3, 0)
        };
        cp.post(new Sum(x, 0));

        assertEquals(0, x[0].min());
        assertEquals(0, x[1].min());
        assertEquals(0, x[2].min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum6(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{
                CPFactory.makeIntVar(cp, -5, 0),
                CPFactory.makeIntVar(cp, -5, 0),
                CPFactory.makeIntVar(cp, -3, 3)
        };
        cp.post(new Sum(x, 0));
        assertEquals(-3, x[0].min());
        assertEquals(-3, x[1].min());

        x[2].removeAbove(0);
        cp.fixPoint();

        assertEquals(0, x[0].min());
        assertEquals(0, x[1].min());
        assertEquals(0, x[2].min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum7(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{
                CPFactory.makeIntVar(cp, -5, 0),
                CPFactory.makeIntVar(cp, -5, 0),
                CPFactory.makeIntVar(cp, -3, 3)
        };
        cp.post(new Sum(x, 0));
        assertEquals(-3, x[0].min());
        assertEquals(-3, x[1].min());

        x[2].remove(1);
        x[2].remove(2);
        x[2].remove(3);
        x[2].remove(4);
        x[2].remove(5);
        cp.fixPoint();

        assertEquals(0, x[0].min());
        assertEquals(0, x[1].min());
        assertEquals(0, x[2].min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum8(CPSolver cp) {

        // {0,0,0},  1
        // {-2,1,1}  3
        // {2,-1,-1} 3
        // {-1,1,0}  6
        // {0,-3,3}  6
        // {2,-2,0}  6
        // {-1,1,0}  6
        // {1,2,-3}  6

        CPIntVar[] x = new CPIntVar[]{
                CPFactory.makeIntVar(cp, -3, 3),
                CPFactory.makeIntVar(cp, -3, 3),
                CPFactory.makeIntVar(cp, -3, 3)
        };
        cp.post(new Sum(x, 0));

        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFail(x));

        SearchStatistics stats = search.solve();

        assertEquals(37, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum9(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{CPFactory.makeIntVar(cp, -9, -9)};
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new Sum(x)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum10(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{CPFactory.makeIntVar(cp, -9, -4)};
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new Sum(x)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum11(CPSolver cp) {
        CPIntVar x = makeIVar(cp, -2147483645, -2147483639, -2147483637);
        CPIntVar y = makeIVar(cp, -2147483645, -2147483638);
        assertDoesNotThrow(() -> cp.post(CPFactory.sum(new CPIntVar[]{x}, y)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum12(CPSolver cp) {
        CPIntVar x = makeIVar(cp, -45, -39, -37);
        CPIntVar y = makeIVar(cp, -45, -3);
        assertDoesNotThrow(() -> cp.post(CPFactory.sum(new CPIntVar[]{x}, y)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void sum13OverFlow(CPSolver cp) {
        CPIntVar x0 = makeIVar(cp, -463872433, -463872431, -463872430, -463872429);
        CPIntVar x1 = makeIVar(cp, -463872438, -463872437, -463872430);
        CPIntVar x2 = makeIVar(cp, -463872432, -463872429);
        CPIntVar x3 = makeIVar(cp, -463872435, -463872434, -463872432, -463872431, -463872430, -463872429);
        CPIntVar x4 = makeIVar(cp, -463872437, -463872436, -463872435, -463872432, -463872431, -463872430, -463872429);
        assertThrowsExactly(IntOverFlowException.class, () -> cp.post(CPFactory.le(CPFactory.sum(x0, x1, x2, x3, x4), 0)));
    }


}
