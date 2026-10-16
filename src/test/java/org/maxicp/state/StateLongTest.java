/*
 * MaxiCP is under MIT License
 * Copyright (c)  2026 UCLouvain
 */

package org.maxicp.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.state.trail.Trailer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StateLongTest extends StateManagerTest {

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testStateLong(StateManager sm) {
        StateLong a = sm.makeStateLong(5L);
        StateLong b = sm.makeStateLong(1L << 40);

        sm.saveState();

        a.setValue(1000L);
        a.increment();
        b.setValue(b.value() + 1);
        assertEquals(1001L, a.value());
        assertEquals((1L << 40) + 1, b.value());

        sm.restoreState();

        assertEquals(5L, a.value());
        assertEquals(1L << 40, b.value());
    }

    @Test
    public void testTrailLongSameValueNotPushed() {
        boolean[] pushed = new boolean[] { false };
        Trailer sm = new Trailer() {
            @Override
            public void pushState(StateEntry entry) {
                pushed[0] = true;
                super.pushState(entry);
            }
        };

        StateLong a = sm.makeStateLong(1000L);
        sm.saveState();
        a.setValue(1000L);
        assertFalse(pushed[0], "State should not have been pushed, as 1000 equals to 1000");
        a.setValue(1001L);
        assertTrue(pushed[0]);
    }
}
