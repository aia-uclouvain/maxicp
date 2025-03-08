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
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;

import static org.junit.jupiter.api.Assertions.*;

class MulTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {

        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPIntVar x = CPFactory.makeIntVar(cp, -10, 10);
        CPIntVar y = CPFactory.makeIntVar(cp, -5, 5);
        cp.post(new Mul(x, b, y));

        assertEquals(-10, x.min());
        assertEquals(10, x.max());
        assertFalse(b.isFixed());
        assertEquals(-5, y.min());
        assertEquals(5, y.max());

        cp.getStateManager().saveState();

        cp.post(CPFactory.eq(b,1));
        y.removeAbove(-2);
        cp.fixPoint();
        assertEquals(-5, y.min());
        assertEquals(-2, y.max());
        assertEquals(-5, x.min());
        assertEquals(-2, x.max());

        cp.getStateManager().restoreState();
        cp.getStateManager().saveState();


        y.removeAbove(-2);
        cp.fixPoint();
        assertTrue(b.isTrue());
        assertEquals(-5, x.min());
        assertEquals(-2, x.max());

        cp.getStateManager().restoreState();
        cp.getStateManager().saveState();

        y.fix(0);
        cp.fixPoint();
        assertFalse(b.isFixed());
        x.fix(0);
        assertFalse(b.isFixed());

        cp.getStateManager().restoreState();
        cp.getStateManager().saveState();

        y.fix(0);
        cp.fixPoint();
        x.removeAbove(-2);
        cp.fixPoint();
        assertTrue(b.isFalse());

    }

}