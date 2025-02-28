/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.datastructures;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.state.StateManager;
import org.maxicp.state.StateManagerTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;


public class StateSparseSetTest extends StateManagerTest {


    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testExample(StateManager sm)  {
        StateSparseSet set = new StateSparseSet(sm, 9, 0);

        sm.saveState();

        set.remove(4);
        set.remove(6);

        assertFalse(set.contains(4));
        assertFalse(set.contains(6));

        sm.restoreState();

        assertTrue(set.contains(4));
        assertTrue(set.contains(6));

    }


    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testReversibleSparseSet(StateManager sm)  {
        StateSparseSet set = new StateSparseSet(sm, 10, 0);

        assertTrue(toSet(set.toArray()).equals(toSet(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9})));

        sm.saveState();

        set.remove(1);
        set.remove(0);

        assertTrue(set.min() == 2);

        set.remove(8);
        set.remove(9);

        assertTrue(toSet(set.toArray()).equals(toSet(new int[]{2, 3, 4, 5, 6, 7})));
        assertTrue(set.max() == 7);

        sm.restoreState();
        sm.saveState();

        assertEquals(10, set.size());

        for (int i = 0; i < 10; i++) {
            assertTrue(set.contains(i));
        }
        assertFalse(set.contains(10));

        assertTrue(set.min() == 0);
        assertTrue(set.max() == 9);

        set.removeAllBut(2);

        for (int i = 0; i < 10; i++) {
            if (i != 2) assertFalse(set.contains(i));
        }
        assertTrue(set.contains(2));
        assertTrue(toSet(set.toArray()).equals(toSet(new int[]{2})));


        sm.restoreState();
        sm.saveState();

        assertEquals(10, set.size());

    }

    private Set<Integer> toSet(int... values) {
        Set<Integer> set = new java.util.HashSet<Integer>();
        for (int v : values) {
            set.add(v);
        }
        return set;
    }


    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testRangeConstructor(StateManager sm)  {
        StateSparseSet set = new StateSparseSet(sm, 10, 0);

        for (int i = 0; i < 10; i++) {
            assertTrue(set.contains(i));
        }

        sm.saveState();

        set.remove(4);
        set.remove(5);
        set.remove(0);
        set.remove(1);

        assertEquals(2, set.min());
        assertEquals(9, set.max());

        sm.saveState();

        set.removeAllBut(7);
        assertEquals(7, set.min());
        assertEquals(7, set.max());


        sm.restoreState();
        sm.restoreState();

        for (int i = 0; i < 10; i++) {
            assertTrue(set.contains(i));
        }
    }


    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testRemoveBelow(StateManager sm)  {
        StateSparseSet set = new StateSparseSet(sm, 10, 0);

        for (int i = 0; i < 10; i++) {
            assertTrue(set.contains(i));
        }

        sm.saveState();


        set.removeBelow(5);


        assertEquals(5, set.min());
        assertEquals(9, set.max());

        sm.saveState();

        set.remove(7);
        set.removeBelow(7);

        assertEquals(8, set.min());

        sm.restoreState();
        sm.restoreState();

        for (int i = 0; i < 10; i++) {
            assertTrue(set.contains(i));
        }
    }


    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testRemoveAbove(StateManager sm)  {
        StateSparseSet set = new StateSparseSet(sm, 10, 0);

        for (int i = 0; i < 10; i++) {
            assertTrue(set.contains(i));
        }

        sm.saveState();


        set.remove(1);
        set.remove(2);

        set.removeAbove(7);

        assertEquals(0, set.min());
        assertEquals(7, set.max());

        sm.saveState();

        set.removeAbove(2);

        assertEquals(0, set.max());

        sm.restoreState();
        sm.restoreState();

        for (int i = 0; i < 10; i++) {
            assertTrue(set.contains(i));
        }
    }
}
