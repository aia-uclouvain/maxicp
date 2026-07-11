/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.datastructures;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.state.StateManager;
import org.maxicp.state.StateManagerTest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StateSparseBitSetTest extends StateManagerTest {


    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testExample(StateManager sm) {
        StateSparseBitSet set = new StateSparseBitSet(sm, 256);

        StateSparseBitSet.SupportBitSet b1 = set.new SupportBitSet(); // [0..59] U [130..255]
        StateSparseBitSet.SupportBitSet b2 = set.new SupportBitSet(); // [60..129]
        StateSparseBitSet.SupportBitSet b3 = set.new SupportBitSet(); // empty

        for (int i = 0; i < 256; i++) {
            if (i < 60 || i >= 130) {
                b1.set(i);
            } else {
                b2.set(i);
            }
        }

        set.intersect(b1); // set is now [0..59] U [130..255]

        assertFalse(set.hasEmptyIntersection(b1));
        assertTrue(set.hasEmptyIntersection(b2));

        sm.saveState();

        set.intersect(b3); // set is now empty

        assertTrue(set.hasEmptyIntersection(b1));
        assertTrue(set.hasEmptyIntersection(b2));
        assertTrue(set.hasEmptyIntersection(b3));

        sm.restoreState();  // set is now [0..59] U [130..255]

        assertTrue(!set.hasEmptyIntersection(b1));

        assertTrue(set.hasEmptyIntersection(b2));


    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testCount(StateManager sm) {
        StateSparseBitSet set = new StateSparseBitSet(sm, 200);
        assertEquals(200, set.count());

        StateSparseBitSet.SupportBitSet b1 = set.new SupportBitSet(); // [0..99]
        for (int i = 0; i < 100; i++) {
            b1.set(i);
        }

        sm.saveState();
        set.intersect(b1);
        assertEquals(100, set.count());
        sm.restoreState();
        assertEquals(200, set.count());
    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testCountIntersectionWithCap(StateManager sm) {
        StateSparseBitSet set = new StateSparseBitSet(sm, 256);

        StateSparseBitSet.SupportBitSet b1 = set.new SupportBitSet(); // [0..149]
        StateSparseBitSet.SupportBitSet b2 = set.new SupportBitSet(); // [140..159]
        for (int i = 0; i < 150; i++) {
            b1.set(i);
        }
        for (int i = 140; i < 160; i++) {
            b2.set(i);
        }

        set.intersect(b1); // set is now [0..149]

        // exact when the cap is reached
        assertEquals(150, set.countIntersection(b1, 100));
        assertEquals(150, set.countIntersection(b1, 150));
        assertEquals(10, set.countIntersection(b2, 5)); // set ∩ b2 = [140..149]
        // below the cap in any case when the exact count is below the cap
        assertTrue(set.countIntersection(b2, 50) < 50);
        assertTrue(set.countIntersection(b1, 200) < 200);
    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testIsSubsetOf(StateManager sm) {
        StateSparseBitSet set = new StateSparseBitSet(sm, 130);

        StateSparseBitSet.SupportBitSet b1 = set.new SupportBitSet(); // [0..99]
        StateSparseBitSet.SupportBitSet b2 = set.new SupportBitSet(); // [0..129]
        StateSparseBitSet.SupportBitSet b3 = set.new SupportBitSet(); // [50..99]
        for (int i = 0; i < 130; i++) {
            if (i < 100)
                b1.set(i);
            b2.set(i);
            if (i >= 50 && i < 100)
                b3.set(i);
        }

        assertTrue(set.isSubsetOf(b2));
        assertFalse(set.isSubsetOf(b1));

        sm.saveState();
        set.intersect(b1); // set is now [0..99]
        assertTrue(set.isSubsetOf(b1));
        assertTrue(set.isSubsetOf(b2));
        assertFalse(set.isSubsetOf(b3));

        set.intersect(b3); // set is now [50..99]
        assertTrue(set.isSubsetOf(b3));

        sm.restoreState(); // set is now [0..129]
        assertFalse(set.isSubsetOf(b1));
        assertTrue(set.isSubsetOf(b2));
    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testCountIntersectionAll(StateManager sm) {
        StateSparseBitSet set = new StateSparseBitSet(sm, 100);

        StateSparseBitSet.SupportBitSet[] sets = new StateSparseBitSet.SupportBitSet[3];
        for (int j = 0; j < 3; j++) {
            sets[j] = set.new SupportBitSet();
        }
        for (int i = 0; i < 80; i++) {
            sets[0].set(i); // [0..79]
        }
        for (int i = 40; i < 100; i++) {
            sets[1].set(i); // [40..99]
        }
        for (int i = 0; i < 100; i += 2) {
            sets[2].set(i); // even bits
        }

        int[] idx = new int[]{0, 1, 2};
        // no set designated: the count of the sparse set itself
        assertEquals(100, set.countIntersectionAll(sets, idx, 0));
        assertEquals(80, set.countIntersectionAll(sets, idx, 1));
        assertEquals(40, set.countIntersectionAll(sets, idx, 2)); // [40..79]
        assertEquals(20, set.countIntersectionAll(sets, idx, 3)); // even in [40..79]

        set.intersect(sets[2]); // set is now the even bits
        assertEquals(20, set.countIntersectionAll(sets, idx, 2));
    }

}