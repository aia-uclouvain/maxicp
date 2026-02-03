/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.exclude;
import static org.maxicp.cp.CPFactory.include;
import static org.maxicp.search.Searches.*;

public class CPSetVarTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testConstruction(CPSolver cp) {
        CPSetVarImpl set = new CPSetVarImpl(cp, 3);

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
        assertThrows(InvalidParameterException.class, () -> new CPSetVarImpl(cp, 0));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInclude(CPSolver cp) {
        CPSetVarImpl set = new CPSetVarImpl(cp, 3);
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
        CPSetVarImpl set = new CPSetVarImpl(cp, 3);
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
        CPSetVarImpl set = new CPSetVarImpl(cp, 3);
        set.include(1);
        set.exclude(2);
        set.include(0);

        cp.fixPoint();
        assertTrue(set.isFixed());

        set = new CPSetVarImpl(cp, 3);
        set.excludeAll();

        cp.fixPoint();

        assertTrue(set.isFixed());

        set = new CPSetVarImpl(cp, 3);
        set.includeAll();

        cp.fixPoint();

        assertTrue(set.isFixed());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFixFromCard(CPSolver cp) {
        CPSetVarImpl set = new CPSetVarImpl(cp, 3);
        set.include(1);
        set.card().fix(1);

        cp.fixPoint();
        assertTrue(set.isFixed());
        assertEquals(2, set.nExcluded());
    }

    public static Supplier<Runnable[]> randomSetBranching(CPSetVar[] sets, Random rand) {
        int[] values = new int[Arrays.stream(sets).mapToInt(CPSetVar::nPossible).max().getAsInt()];
        return () -> {
            // select a non fixed set
            CPSetVar set = selectMin(sets, s -> !s.isFixed(), s -> rand.nextInt());
            if (set == null) {
                return EMPTY;
            }
            // select a random value
            int pos = set.fillPossible(values);
            int v = values[rand.nextInt(pos)];
            // create the branching
            return branch(
                    () -> set.getSolver().post(include(set, v)),
                    () -> set.getSolver().post(exclude(set, v))
            );
        };
    }
}