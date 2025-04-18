/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;

import java.security.InvalidParameterException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class CPSetVarTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testConstruction(CPSolver cp) {
        CPSetVar set = new CPSetVar(cp, 3);

        assertEquals(0, set.card().min());
        assertEquals(3, set.card().max());
        assertEquals(0, set.nIncluded());
        assertEquals(3, set.nPossible());
        assertEquals(0, set.nExcluded());
        assertFalse(set.isFixed());

        for (int i = 0; i < 3; i++) {
            assertTrue(set.isPossible(i));
            assertFalse(set.isExcluded(i));
            assertFalse(set.isIncluded(i));
        }
        assertThrows(InvalidParameterException.class, () -> new CPSetVar(cp, 0));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInclude(CPSolver cp) {
        CPSetVar set = new CPSetVar(cp, 3);
        set.include(1);

        assertTrue(set.isIncluded(1));

        cp.fixPoint();

        assertEquals(1, set.card().min());
        assertEquals(3, set.card().max());
        assertEquals(1, set.nIncluded());
        assertEquals(2, set.nPossible());
        assertEquals(0, set.nExcluded());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testExclude(CPSolver cp) {
        CPSetVar set = new CPSetVar(cp, 3);
        set.exclude(1);

        assertTrue(set.isExcluded(1));

        cp.fixPoint();

        assertEquals(0, set.card().min());
        assertEquals(2, set.card().max());
        assertEquals(0, set.nIncluded());
        assertEquals(2, set.nPossible());
        assertEquals(1, set.nExcluded());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFix(CPSolver cp) {
        CPSetVar set = new CPSetVar(cp, 3);
        set.include(1);
        set.exclude(2);
        set.include(0);

        cp.fixPoint();
        assertTrue(set.isFixed());

        set = new CPSetVar(cp, 3);
        set.excludeAll();

        cp.fixPoint();

        assertTrue(set.isFixed());

        set = new CPSetVar(cp, 3);
        set.includeAll();

        cp.fixPoint();

        assertTrue(set.isFixed());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFixFromCard(CPSolver cp) {
        CPSetVar set = new CPSetVar(cp, 3);
        set.include(1);
        set.card().fix(1);

        cp.fixPoint();
        assertTrue(set.isFixed());
        assertEquals(2, set.nExcluded());
    }
}