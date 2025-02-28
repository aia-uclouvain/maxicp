/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.cp.CPFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;


public class CPIntVarTest extends CPSolverTest {


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testIntVar(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, 10);
        CPIntVar y = CPFactory.makeIntVar(cp, 10);

        cp.getStateManager().saveState();

        assertFalse(x.isFixed());
        x.remove(5);
        assertEquals(9, x.size());
        x.fix(7);
        assertEquals(1, x.size());
        assertTrue(x.isFixed());
        assertEquals(7, x.min());
        assertEquals(7, x.max());

        assertThrowsExactly(InconsistencyException.class, () -> x.fix(8));


        cp.getStateManager().restoreState();
        cp.getStateManager().saveState();

        assertFalse(x.isFixed());
        assertEquals(10, x.size());

        for (int i = 0; i < 10; i++) {
            assertTrue(x.contains(i));
        }
        assertFalse(x.contains(-1));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void onDomainChangeOnBind(CPSolver cp) {
        AtomicBoolean propagateCalled = new AtomicBoolean(false);

        CPIntVar x = CPFactory.makeIntVar(cp, 10);
        CPIntVar y = CPFactory.makeIntVar(cp, 10);

        CPConstraint cons = new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                x.whenFixed(() -> propagateCalled.set(true));
                y.whenDomainChange(() -> propagateCalled.set(true));
            }
        };

        cp.post(cons);
        x.remove(8);
        cp.fixPoint();
        assertFalse(propagateCalled.get());
        x.fix(4);
        cp.fixPoint();
        assertTrue(propagateCalled.get());
        propagateCalled.set(false);
        y.remove(10);
        cp.fixPoint();
        assertFalse(propagateCalled.get());
        y.remove(9);
        cp.fixPoint();
        assertTrue(propagateCalled.get());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void arbitraryRangeDomains(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, -10, 10);

        cp.getStateManager().saveState();

        assertFalse(x.isFixed());
        x.remove(-9);
        x.remove(-10);


        assertEquals(19, x.size());
        x.fix(-4);
        assertEquals(1, x.size());
        assertTrue(x.isFixed());
        assertEquals(-4, x.min());

        assertThrowsExactly(InconsistencyException.class, () -> x.fix(8));

        cp.getStateManager().restoreState();

        assertEquals(21, x.size());

        for (int i = -10; i < 10; i++) {
            assertTrue(x.contains(i));
        }
        assertFalse(x.contains(-11));

    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void arbitrarySetDomains(CPSolver cp) {

        Set<Integer> dom = new HashSet<>(Arrays.asList(-7, -10, 6, 9, 10, 12));

        CPIntVar x = CPFactory.makeIntVar(cp, dom);

        cp.getStateManager().saveState();

        for (int i = -15; i < 15; i++) {
            if (dom.contains(i))
                assertTrue(x.contains(i));
            else assertFalse(x.contains(i));
        }

        x.fix(-7);

        assertThrowsExactly(InconsistencyException.class, () -> x.fix(-10));

        cp.getStateManager().restoreState();

        for (int i = -15; i < 15; i++) {
            if (dom.contains(i)) assertTrue(x.contains(i));
            else assertFalse(x.contains(i));
        }
        assertEquals(6, x.size());

    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void onBoundChange(CPSolver cp) {

        AtomicBoolean propagateCalled = new AtomicBoolean(false);
        CPIntVar x = CPFactory.makeIntVar(cp, 10);
        CPIntVar y = CPFactory.makeIntVar(cp, 10);

        CPConstraint cons = new AbstractCPConstraint(cp) {

            @Override
            public void post() {
                x.whenFixed(() -> propagateCalled.set(true));
                y.whenDomainChange(() -> propagateCalled.set(true));
            }
        };

        cp.post(cons);
        x.remove(8);
        cp.fixPoint();
        assertFalse(propagateCalled.get());
        x.remove(9);
        cp.fixPoint();
        assertFalse(propagateCalled.get());
        x.fix(4);
        cp.fixPoint();
        assertTrue(propagateCalled.get());
        propagateCalled.set(false);
        assertFalse(y.contains(10));
        y.remove(10);
        cp.fixPoint();
        assertFalse(propagateCalled.get());
        propagateCalled.set(false);
        y.remove(2);
        cp.fixPoint();
        assertTrue(propagateCalled.get());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void removeAbove(CPSolver cp) {

        AtomicBoolean propagateCalled = new AtomicBoolean(false);
        CPIntVar x = CPFactory.makeIntVar(cp, 10);

        CPConstraint cons = new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                x.propagateOnBoundChange(this);
            }

            @Override
            public void propagate() {
                propagateCalled.set(true);
            }
        };

        cp.post(cons);
        x.remove(8);
        cp.fixPoint();
        assertFalse(propagateCalled.get());
        x.removeAbove(8);
        assertEquals(7, x.max());
        cp.fixPoint();
        assertTrue(propagateCalled.get());

    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void removeBelow(CPSolver cp) {
        AtomicBoolean propagateCalled = new AtomicBoolean(false);
        CPIntVar x = CPFactory.makeIntVar(cp, 10);

        CPConstraint cons = new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                x.propagateOnBoundChange(this);
            }

            @Override
            public void propagate() {
                propagateCalled.set(true);
            }
        };

        cp.post(cons);
        x.remove(3);
        cp.fixPoint();
        assertFalse(propagateCalled.get());
        x.removeBelow(3);
        assertEquals(4, x.min());
        cp.fixPoint();
        assertTrue(propagateCalled.get());
        propagateCalled.set(false);

        x.removeBelow(5);
        assertEquals(5, x.min());
        cp.fixPoint();
        assertTrue(propagateCalled.get());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void fillArray(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, 2, 9);
        x.remove(3);
        x.remove(5);
        x.remove(2);
        x.remove(9);

        int[] values = new int[10];
        int s = x.fillArray(values);
        HashSet<Integer> dom = new HashSet<Integer>();
        for (int i = 0; i < s; i++) {
            dom.add(values[i]);
        }
        HashSet<Integer> expectedDom = new HashSet<Integer>();
        Collections.addAll(expectedDom, 4, 6, 7, 8);
        assertEquals(expectedDom, dom);

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void arbitrarySetDomainsMaxInt(CPSolver cp) {
        Set<Integer> dom = Set.of(2147483645);
        CPIntVar var1 = CPFactory.makeIntVar(cp, dom);
        assertEquals(2147483645, var1.max());
    }


}
