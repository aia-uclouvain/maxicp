/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.util.exception.InconsistencyException;

import static org.junit.jupiter.api.Assertions.*;

public class CPItemsetVarTest extends CPSolverTest {

    /**
     * The database of Pasquier et al. 1999:
     * t0={1,3,4}, t1={2,3,5}, t2={1,2,3,5}, t3={2,5}, t4={1,2,3,5}
     */
    private static TransactionDb pasquierDb() {
        return new TransactionDb("contextPasquier99", new int[][]{
                {1, 3, 4}, {2, 3, 5}, {1, 2, 3, 5}, {2, 5}, {1, 2, 3, 5}});
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInitialState(CPSolver cp) {
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, pasquierDb());
        assertEquals(6, p.nItems());
        assertEquals(0, p.nRequired());
        assertEquals(6, p.nPossible());
        assertEquals(0, p.nExcluded());
        assertFalse(p.isFixed());
        assertEquals(5, p.coverSize());
        // extension supports of the empty pattern are the item supports
        assertEquals(0, p.supportAfter(0));
        assertEquals(3, p.supportAfter(1));
        assertEquals(4, p.supportAfter(2));
        assertEquals(4, p.supportAfter(3));
        assertEquals(1, p.supportAfter(4));
        assertEquals(4, p.supportAfter(5));
        assertEquals(0, p.size().min());
        assertEquals(6, p.size().max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRequireUpdatesCover(CPSolver cp) {
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, pasquierDb());
        p.require(1);
        cp.fixPoint();
        assertTrue(p.isRequired(1));
        assertEquals(1, p.nRequired());
        assertEquals(3, p.coverSize()); // t0, t2, t4
        assertEquals(3, p.supportAfter(3)); // 1 and 3 in t0, t2, t4
        assertEquals(2, p.supportAfter(2)); // 1 and 2 in t2, t4
        assertEquals(1, p.supportAfter(4)); // 1 and 4 in t0
        assertEquals(2, p.supportAfter(5));
        assertTrue(p.coverSubsetOf(3));
        assertFalse(p.coverSubsetOf(2));

        p.require(2);
        cp.fixPoint();
        assertEquals(2, p.coverSize()); // t2, t4
        assertTrue(p.coverSubsetOf(5));
        assertEquals(2, p.size().min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testExclude(CPSolver cp) {
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, pasquierDb());
        p.exclude(3);
        cp.fixPoint();
        assertTrue(p.isExcluded(3));
        assertEquals(5, p.coverSize()); // exclusion does not change the cover
        assertEquals(5, p.size().max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRequireExcludedFails(CPSolver cp) {
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, pasquierDb());
        p.exclude(2);
        assertThrowsExactly(InconsistencyException.class, () -> p.require(2));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testExcludeRequiredFails(CPSolver cp) {
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, pasquierDb());
        p.require(2);
        assertThrowsExactly(InconsistencyException.class, () -> p.exclude(2));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFixedWhenNoPossible(CPSolver cp) {
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, pasquierDb());
        p.require(1);
        p.require(3);
        for (int i : new int[]{0, 2, 4, 5}) {
            p.exclude(i);
        }
        cp.fixPoint();
        assertTrue(p.isFixed());
        assertEquals(2, p.nRequired());
        assertEquals(3, p.coverSize()); // support of {1,3}
        assertTrue(p.size().isFixed());
        assertEquals(2, p.size().min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSaveRestoreCoverSoundness(CPSolver cp) {
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, pasquierDb());
        p.require(1);
        cp.fixPoint();
        int coverBefore = p.coverSize();
        int[] supportsBefore = new int[6];
        for (int i = 0; i < 6; i++) {
            supportsBefore[i] = p.supportAfter(i);
        }

        cp.getStateManager().saveState();
        p.require(2);
        p.exclude(4);
        cp.fixPoint();
        assertEquals(2, p.coverSize());
        cp.getStateManager().restoreState();

        assertEquals(coverBefore, p.coverSize());
        assertEquals(1, p.nRequired());
        assertEquals(5, p.nPossible());
        for (int i = 0; i < 6; i++) {
            assertEquals(supportsBefore[i], p.supportAfter(i));
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEvents(CPSolver cp) {
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, pasquierDb());
        int[] counters = new int[2]; // domain, fix

        cp.post(new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                p.propagateOnDomainChange(this);
            }

            @Override
            public void propagate() {
                counters[0]++;
            }
        });
        cp.post(new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                p.propagateOnFix(this);
            }

            @Override
            public void propagate() {
                counters[1]++;
            }
        });

        p.require(1);
        cp.fixPoint();
        assertEquals(1, counters[0]);
        assertEquals(0, counters[1]);

        p.exclude(4);
        cp.fixPoint();
        assertEquals(2, counters[0]);
        assertEquals(0, counters[1]);

        // fixing the variable triggers both events
        for (int i : new int[]{0, 2, 3, 5}) {
            p.exclude(i);
        }
        cp.fixPoint();
        assertTrue(p.isFixed());
        assertEquals(1, counters[1]);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSizeViewChanneling(CPSolver cp) {
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, pasquierDb());
        p.size().removeAbove(0);
        cp.fixPoint();
        assertTrue(p.isFixed());
        assertEquals(0, p.nRequired());
        assertEquals(6, p.nExcluded());

        CPItemsetVar q = CPFactory.makeItemsetVar(cp, pasquierDb());
        q.size().removeBelow(6);
        cp.fixPoint();
        assertTrue(q.isFixed());
        assertEquals(6, q.nRequired());
        assertEquals(0, q.coverSize()); // no transaction contains item 0
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSupportView(CPSolver cp) {
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, pasquierDb());
        CPIntVar support = p.support();
        assertSame(support, p.support());
        assertEquals(5, support.max());
        assertEquals(0, support.min());

        // requiring a minimum support excludes infrequent items
        support.removeBelow(3);
        cp.fixPoint();
        assertTrue(p.isExcluded(0)); // support 0
        assertTrue(p.isExcluded(4)); // support 1
        assertFalse(p.isExcluded(1));

        p.require(1);
        cp.fixPoint();
        assertEquals(3, support.max()); // cover of {1} is 3
        // support is now fixed to 3: cover-reducing items are excluded, closure items remain
        assertTrue(p.isExcluded(2)); // {1,2} has support 2
        assertTrue(p.isExcluded(5)); // {1,5} has support 2
        assertTrue(p.isPossible(3) || p.isRequired(3)); // {1,3} has support 3
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSupportViewFixedOnLeaf(CPSolver cp) {
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, pasquierDb());
        CPIntVar support = p.support();
        p.require(2);
        p.require(3);
        for (int i : new int[]{0, 1, 4, 5}) {
            p.exclude(i);
        }
        cp.fixPoint();
        assertTrue(p.isFixed());
        assertTrue(support.isFixed());
        assertEquals(3, support.min()); // {2,3} in t1, t2, t4
    }
}
