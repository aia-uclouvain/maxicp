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
import org.maxicp.cp.CPFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbsoluteTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest0(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -5, 5);
        CPIntVar y = CPFactory.makeIntVar(cp, -10, 10);

        cp.post(new Absolute(x, y));

        assertEquals(0, y.min());
        assertEquals(5, y.max());
        assertEquals(11, x.size());

        x.removeAbove(-2);
        cp.fixPoint();

        assertEquals(2, y.min());

        x.removeBelow(-4);
        cp.fixPoint();

        assertEquals(4, y.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest1(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -5, 5);
        CPIntVar y = CPFactory.makeIntVar(cp, -10, 10);

        cp.post(CPFactory.neq(x, 0));
        cp.post(CPFactory.neq(x, 5));
        cp.post(CPFactory.neq(x, -5));

        cp.post(new Absolute(x, y));

        assertEquals(1, y.min());
        assertEquals(4, y.max());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest2(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -5, 0);
        CPIntVar y = CPFactory.makeIntVar(cp, 4, 4);

        cp.post(new Absolute(x, y));

        assertTrue(x.isFixed());
        assertTrue(y.isFixed());
        assertEquals(-4, x.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest3(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, 7, 7);
        CPIntVar y = CPFactory.makeIntVar(cp, -1000, 12);

        cp.post(new Absolute(x, y));

        assertTrue(x.isFixed());
        assertTrue(y.isFixed());
        assertEquals(7, y.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest4(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -5, 10);
        CPIntVar y = CPFactory.makeIntVar(cp, -6, 7);

        cp.post(new Absolute(x, y));

        assertEquals(7, x.max());
        assertEquals(-5, x.min());

        cp.post(CPFactory.neq(y, 0));

        cp.post(CPFactory.le(x,4));

        assertEquals(5, y.max());

        cp.post(CPFactory.le(x,-2));

        assertEquals(2, y.min());

        y.removeBelow(5);
        cp.fixPoint();

        assertTrue(x.isFixed());
        assertTrue(y.isFixed());
    }

}
