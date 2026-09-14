/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A reversible value created after a save and changed at that same level must
 * come back to its initial value when the
 * level is restored, as happens when a constraint allocates reversible state
 * lazily during the search.
 */
public class StateCreationLevelTest extends StateManagerTest {

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void anIntChangedAtTheLevelItWasCreatedAtIsRestored(StateManager sm) {
        sm.saveState();
        StateInt v = sm.makeStateInt(5);
        v.setValue(7);
        sm.restoreState();
        assertEquals(5, v.value());
    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void aLongChangedAtTheLevelItWasCreatedAtIsRestored(StateManager sm) {
        sm.saveState();
        StateLong v = sm.makeStateLong(5L);
        v.setValue(7L);
        sm.restoreState();
        assertEquals(5L, v.value());
    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void aReferenceChangedAtTheLevelItWasCreatedAtIsRestored(StateManager sm) {
        sm.saveState();
        State<String> v = sm.makeStateRef("initial");
        v.setValue("changed");
        sm.restoreState();
        assertEquals("initial", v.value());
    }
}
