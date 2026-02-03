/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.util.exception.IntOverFlowException;
import org.maxicp.cp.CPFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class CPIntVarViewOffsetTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testIntVar(CPSolver cp) {

        CPIntVar x = CPFactory.plus(CPFactory.makeIntVar(cp, -3, 4), 3); // domain is {0,1,2,3,4,5,6,7}

        assertEquals(0, x.min());
        assertEquals(7, x.max());
        assertEquals(8, x.size());

        cp.getStateManager().saveState();

        assertFalse(x.isFixed());

        x.remove(0);
        assertFalse(x.contains(0));
        x.remove(3);
        assertTrue(x.contains(1));
        assertTrue(x.contains(2));
        assertEquals(6, x.size());
        x.removeAbove(6);
        assertEquals(6, x.max());
        x.removeBelow(3);
        assertEquals(4, x.min());
        x.fix(5);
        assertTrue(x.isFixed());
        assertEquals(5, x.max());

        assertThrowsExactly(InconsistencyException.class, () -> x.fix(4));

        cp.getStateManager().restoreState();

        assertEquals(8, x.size());
        assertFalse(x.contains(-1));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void onDomainChangeOnBind(CPSolver cp) {
        AtomicBoolean propagateCalled = new AtomicBoolean(false);

        CPIntVar x = CPFactory.plus(CPFactory.makeIntVar(cp, 10), 1); // 1..11
        CPIntVar y = CPFactory.plus(CPFactory.makeIntVar(cp, 10), 1); // 1..11

        CPConstraint cons = new AbstractCPConstraint(cp) {

            @Override
            public void post() {
                x.whenFixed(() -> propagateCalled.set(true));
                y.whenDomainChange(() -> propagateCalled.set(true));
            }
        };

        cp.post(cons);
        x.remove(9);
        cp.fixPoint();
        assertFalse(propagateCalled.get());
        x.fix(5);
        cp.fixPoint();
        assertTrue(propagateCalled.get());
        propagateCalled.set(false);
        y.remove(11);
        cp.fixPoint();
        assertFalse(propagateCalled.get());
        y.remove(10);
        cp.fixPoint();
        assertTrue(propagateCalled.get());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void onBoundChange(CPSolver cp) {
        CPIntVar x = CPFactory.plus(CPFactory.makeIntVar(cp, 10), 1);
        CPIntVar y = CPFactory.plus(CPFactory.makeIntVar(cp, 10), 1);

        AtomicBoolean propagateCalled = new AtomicBoolean(false);

        CPConstraint cons = new AbstractCPConstraint(cp) {

            @Override
            public void post() {
                x.whenFixed(() -> propagateCalled.set(true));
                y.whenDomainChange(() -> propagateCalled.set(true));
            }
        };

        cp.post(cons);
        x.remove(9);
        cp.fixPoint();
        assertFalse(propagateCalled.get());
        x.remove(10);
        cp.fixPoint();
        assertFalse(propagateCalled.get());
        x.fix(5);
        cp.fixPoint();
        assertTrue(propagateCalled.get());
        propagateCalled.set(false);
        assertFalse(y.contains(11));
        y.remove(11);
        cp.fixPoint();
        assertFalse(propagateCalled.get());
        propagateCalled.set(false);
        y.remove(3);
        cp.fixPoint();
        assertTrue(propagateCalled.get());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOverFlow(CPSolver cp) {
        assertThrowsExactly(IntOverFlowException.class, () -> CPFactory.plus(CPFactory.makeIntVar(cp, Integer.MAX_VALUE - 5, Integer.MAX_VALUE - 2), 3));
    }


}
