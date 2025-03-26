/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.util.exception.InconsistencyException;

import static org.junit.jupiter.api.Assertions.*;

public class MulCteResTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, 2, 5);
        CPIntVar y = CPFactory.makeIntVar(cp, 2, 5);
        cp.post(new MulCteRes(x, y, 4));
        assertEquals(2,x.max());
        assertEquals(2,y.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test2(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -2, 2);
        CPIntVar y = CPFactory.makeIntVar(cp, -2, 2);
        cp.post(new MulCteRes(x, y, 0));
        cp.post(CPFactory.neq(x, 0));
        assertEquals(0,y.min());
        assertEquals(0,y.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test3(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -2, 2);
        CPIntVar y = CPFactory.makeIntVar(cp, -2, 2);
        cp.post(new MulCteRes(y, x, 0));
        cp.post(CPFactory.neq(x, 0));
        assertEquals(0,y.min());
        assertEquals(0,y.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test4(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -2, 5);
        CPIntVar y = CPFactory.makeIntVar(cp, 2, 5);
        cp.post(new MulCteRes(x, y, 0));
        assertEquals(0,x.min());
        assertEquals(0,x.max());
        assertEquals(2,y.min());
        assertEquals(5,y.max());
    }

}
