/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SquareTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -1, 0);
        CPIntVar y = CPFactory.makeIntVar(cp, 0, 100);

        cp.post(new Square(x, y));
        assertEquals(0, y.min());
        assertEquals(1,y.max());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test2(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, -10, -1);
        CPIntVar y = CPFactory.makeIntVar(cp, 3, 9);
        cp.post(new Square(x, y));

        assertEquals(-3, x.min());
        assertEquals(-2, x.max());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test3(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, -10, -1);
        CPIntVar y = CPFactory.makeIntVar(cp, 3, 9);
        cp.post(new Square(x, y));

        assertEquals(-3, x.min());
        assertEquals(-2, x.max());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test4(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, -10, 10);
        CPIntVar y = CPFactory.makeIntVar(cp, -1000, 1000);
        cp.post(new Square(x, y));
        assertEquals(0, y.min());
        assertEquals(100, y.max());
        cp.post(CPFactory.ge(x,5));
        assertEquals(25, y.min());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test5(CPSolver cp) {

        CPIntVar x = CPFactory.mul(CPFactory.makeIntVar(cp, -5, 5),2);
        CPIntVar y = CPFactory.makeIntVar(cp, -1000, 1000);
        cp.post(new Square(x, y));
        assertEquals(0, y.min());
        assertEquals(100, y.max());

        cp.post(CPFactory.neq(x,0));
        assertEquals(4, y.min());

    }

}