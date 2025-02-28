/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StateBoolTest extends StateManagerTest {

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testStateBool(StateManager sm)  {

        State<Boolean> b1 = sm.makeStateRef(true);
        State<Boolean> b2 = sm.makeStateRef(false);

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

        State<Boolean> a = sm.makeStateRef(true);
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


}
