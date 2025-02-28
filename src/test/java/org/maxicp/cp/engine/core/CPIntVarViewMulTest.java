/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.util.exception.IntOverFlowException;
import org.maxicp.cp.CPFactory;

import static org.junit.jupiter.api.Assertions.*;


public class CPIntVarViewMulTest extends CPSolverTest {

    public boolean propagateCalled = false;

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testIntVar(CPSolver cp) {

        CPIntVar x = CPFactory.mul(CPFactory.mul(CPFactory.makeIntVar(cp, -3, 4), -3), -1); // domain is {-9,-6,-3,0,3,6,9,12}

        assertEquals(-9, x.min());
        assertEquals(12, x.max());
        assertEquals(8, x.size());

        cp.getStateManager().saveState();

        assertFalse(x.isFixed());

        x.remove(-6);
        assertFalse(x.contains(-6));
        x.remove(2);
        assertTrue(x.contains(0));
        assertTrue(x.contains(3));
        assertEquals(7, x.size());
        x.removeAbove(7);
        assertEquals(6, x.max());
        x.removeBelow(-8);
        assertEquals(-3, x.min());
        x.fix(3);
        assertTrue(x.isFixed());
        assertEquals(3, x.max());

        assertThrowsExactly(InconsistencyException.class, () -> x.fix(8));
        cp.getStateManager().restoreState();

        assertEquals(8, x.size());
        assertFalse(x.contains(-1));

    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void onDomainChangeOnBind(CPSolver cp) {
        propagateCalled = false;

        CPIntVar x = CPFactory.mul(CPFactory.makeIntVar(cp, 10), 1);
        CPIntVar y = CPFactory.mul(CPFactory.makeIntVar(cp, 10), 1);

        CPConstraint cons = new AbstractCPConstraint(cp) {

            @Override
            public void post() {
                x.whenFixed(() -> propagateCalled = true);
                y.whenDomainChange(() -> propagateCalled = true);
            }
        };

        cp.post(cons);
        x.remove(8);
        cp.fixPoint();
        assertFalse(propagateCalled);
        x.fix(4);
        cp.fixPoint();
        assertTrue(propagateCalled);
        propagateCalled = false;
        y.remove(10);
        cp.fixPoint();
        assertFalse(propagateCalled);
        y.remove(9);
        cp.fixPoint();
        assertTrue(propagateCalled);

    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void onBoundChange(CPSolver cp) {

        CPIntVar x = CPFactory.mul(CPFactory.makeIntVar(cp, 10), 1);
        CPIntVar y = CPFactory.mul(CPFactory.makeIntVar(cp, 10), 1);

        CPConstraint cons = new AbstractCPConstraint(cp) {

            @Override
            public void post() {
                x.whenFixed(() -> propagateCalled = true);
                y.whenDomainChange(() -> propagateCalled = true);
            }
        };

        cp.post(cons);
        x.remove(8);
        cp.fixPoint();
        assertFalse(propagateCalled);
        x.remove(9);
        cp.fixPoint();
        assertFalse(propagateCalled);
        x.fix(4);
        cp.fixPoint();
        assertTrue(propagateCalled);
        propagateCalled = false;
        assertFalse(y.contains(10));
        y.remove(10);
        cp.fixPoint();
        assertFalse(propagateCalled);
        propagateCalled = false;
        y.remove(2);
        cp.fixPoint();
        assertTrue(propagateCalled);

    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOverFlow(CPSolver cp) {
        assertThrowsExactly(IntOverFlowException.class, () -> CPFactory.mul(CPFactory.makeIntVar(cp, 1000000, 1000000), 10000000));
    }


}
