/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StateMapTest extends StateManagerTest {

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testExample(StateManager sm) {
        StateMap<Integer, Integer> map = sm.makeStateMap();
        map.put(1, 2);
        map.put(2, 3);
        sm.saveState();
        map.put(1, 3);
        map.put(2, 4);
        sm.restoreState();
        assertEquals(Integer.valueOf(2), map.get(1));
        assertEquals(Integer.valueOf(3), map.get(2));
    }

}
