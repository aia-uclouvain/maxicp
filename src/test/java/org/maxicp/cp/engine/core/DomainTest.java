/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class DomainTest extends CPSolverTest {

    private static class MyDomainListener implements DomainListener {

        int nBind = 0;
        int nChange = 0;
        int nRemoveBelow = 0;
        int nRemoveAbove = 0;

        @Override
        public void empty() {
        }

        @Override
        public void bind() {
            nBind++;
        }

        @Override
        public void change() {
            nChange++;
        }

        @Override
        public void changeMin() {
            nRemoveBelow++;
        }

        @Override
        public void changeMax() {
            nRemoveAbove++;
        }
    }

    ;

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDomain1(CPSolver cp) {
        MyDomainListener dlistener = new MyDomainListener();
        IntDomain dom = new SparseSetDomain(cp.getStateManager(), 5, 10);

        dom.removeAbove(8, dlistener);

        assertEquals(1, dlistener.nChange);
        assertEquals(0, dlistener.nBind);
        assertEquals(1, dlistener.nRemoveAbove);
        assertEquals(0, dlistener.nRemoveBelow);

        dom.remove(6, dlistener);

        assertEquals(2, dlistener.nChange);
        assertEquals(0, dlistener.nBind);
        assertEquals(1, dlistener.nRemoveAbove);
        assertEquals(0, dlistener.nRemoveBelow);

        dom.remove(5, dlistener);

        assertEquals(3, dlistener.nChange);
        assertEquals(0, dlistener.nBind);
        assertEquals(1, dlistener.nRemoveAbove);
        assertEquals(1, dlistener.nRemoveBelow);

        dom.remove(7, dlistener);

        assertEquals(4, dlistener.nChange);
        assertEquals(1, dlistener.nBind);
        assertEquals(1, dlistener.nRemoveAbove);
        assertEquals(2, dlistener.nRemoveBelow);

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDomain2(CPSolver cp) {
        MyDomainListener dlistener = new MyDomainListener();
        IntDomain dom = new SparseSetDomain(cp.getStateManager(), 5, 10);

        dom.removeAllBut(7, dlistener);

        assertEquals(1, dlistener.nChange);
        assertEquals(1, dlistener.nBind);
        assertEquals(1, dlistener.nRemoveAbove);
        assertEquals(1, dlistener.nRemoveBelow);

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDomain3(CPSolver cp) {
        MyDomainListener dlistener = new MyDomainListener();
        IntDomain dom = new SparseSetDomain(cp.getStateManager(), 5, 10);

        dom.removeAbove(5, dlistener);

        assertEquals(1, dlistener.nChange);
        assertEquals(1, dlistener.nBind);
        assertEquals(1, dlistener.nRemoveAbove);
        assertEquals(0, dlistener.nRemoveBelow);

    }


}
