/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.state.trail.Trailer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StateBoolTest extends StateManagerTest {

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testStateBool(StateManager sm)  {

        StateBoolean b1 = sm.makeStateBoolean(true);
        StateBoolean b2 = sm.makeStateBoolean(false);

        sm.saveState();

        b1.setValue(true);
        b1.setValue(false);
        b1.setValue(true);

        b2.setValue(false);
        b2.setValue(true);

        sm.restoreState();

        assertTrue(b1.value());
        assertFalse(b2.value());

    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void bugMagicOnRestore(StateManager sm)  {

        StateBoolean a = sm.makeStateBoolean(true);
        // level 0, a is true

        sm.saveState(); // level 1, a is true recorded
        sm.saveState(); // level 2, a is true recorded

        a.setValue(false);

        sm.restoreState(); // level 1, a is true

        a.setValue(false); // level 1, a is false

        sm.saveState(); // level 2, a is false recorded

        sm.restoreState(); // level 1 a is false
        sm.restoreState(); // level 0 a is true

        assertTrue(a.value());

    }

    @Test
    public void testTrailBooleanSameValueNotPushed() {
        boolean[] pushed = new boolean[] { false };
        Trailer sm = new Trailer() {
            @Override
            public void pushState(StateEntry entry) {
                pushed[0] = true;
                super.pushState(entry);
            }
        };

        StateBoolean a = sm.makeStateBoolean(true);
        sm.saveState();
        a.setValue(true);
        assertFalse(pushed[0], "State should not have been pushed, as true equals to true");
        a.setValue(false);
        assertTrue(pushed[0]);
    }

}
