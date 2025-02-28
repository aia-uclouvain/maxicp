/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.datastructures;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.state.StateManager;
import org.maxicp.state.StateManagerTest;
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


}