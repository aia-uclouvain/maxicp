/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StateIntTest extends StateManagerTest {

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testExample(StateManager sm) {

        // Two reversible int's inside the sm
        StateInt a = sm.makeStateInt(5);
        StateInt b = sm.makeStateInt(9);

        a.setValue(7);
        b.setValue(13);

        // Record current state a=7, b=1 and increase the level to 0
        sm.saveState();
        assertEquals(0, sm.getLevel());

        a.setValue(10);
        b.setValue(13);
        a.setValue(11);

        // Record current state a=11, b=13 and increase the level to 1
        sm.saveState();
        assertEquals(1, sm.getLevel());

        a.setValue(4);
        b.setValue(9);

        // Restore the state recorded at the top level 1: a=11, b=13
        // and remove the state of that level
        sm.restoreState();

        assertEquals(Integer.valueOf(11), a.value());
        assertEquals(Integer.valueOf(13), b.value());
        assertEquals(0, sm.getLevel());

        // Restore the state recorded at the top level 0: a=7, b=13
        // and remove the state of that level
        sm.restoreState();

        assertEquals(Integer.valueOf(7), a.value());
        assertEquals(Integer.valueOf(13), b.value());
        assertEquals(-1, sm.getLevel());

    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testReversibleInt(StateManager sm) {

        StateInt a = sm.makeStateInt(5);
        StateInt b = sm.makeStateInt(5);
        assertTrue(a.value() == 5);
        a.setValue(7);
        b.setValue(13);
        assertTrue(a.value() == 7);

        sm.saveState();

        a.setValue(10);
        assertTrue(a.value() == 10);
        a.setValue(11);
        assertTrue(a.value() == 11);
        b.setValue(16);
        b.setValue(15);

        sm.restoreState();
        assertTrue(a.value() == 7);
        assertTrue(b.value() == 13);

    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testPopUntill(StateManager sm) {

        StateInt a = sm.makeStateInt(5);
        StateInt b = sm.makeStateInt(5);

        a.setValue(7);
        b.setValue(13);
        a.setValue(13);

        sm.saveState(); // level 0

        a.setValue(5);
        b.setValue(10);

        StateInt c = sm.makeStateInt(5);

        sm.saveState(); // level 1

        a.setValue(8);
        b.setValue(1);
        c.setValue(10);

        sm.saveState(); // level 2

        a.setValue(10);
        b.setValue(13);
        b.setValue(16);

        sm.saveState(); // level 3

        a.setValue(8);
        b.setValue(10);

        sm.restoreStateUntil(0);

        //assertEquals(0,sm.getLevel());

        sm.saveState(); // level 1

        //assertEquals(1,sm.getLevel());
        assertEquals(Integer.valueOf(5), a.value());
        assertEquals(Integer.valueOf(10), b.value());
        assertEquals(Integer.valueOf(5), c.value());

        a.setValue(8);
        b.setValue(10);
        b.setValue(8);
        b.setValue(10);

        sm.restoreStateUntil(0);

        //assertEquals(0,sm.getLevel());
        assertEquals(Integer.valueOf(5), a.value());
        assertEquals(Integer.valueOf(10), b.value());
        assertEquals(Integer.valueOf(5), c.value());


    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testPopUntillEasy(StateManager sm) {

        StateInt a = sm.makeStateInt(5);

        a.setValue(7);
        a.setValue(13);

        sm.saveState(); // level 0

        a.setValue(6);


        sm.saveState(); // level 1

        a.setValue(8);

        sm.saveState(); // level 2

        a.setValue(10);

        sm.saveState(); // level 3

        a.setValue(8);

        sm.restoreStateUntil(0);

        sm.saveState(); // level 1

        //assertEquals(1,sm.getLevel());
        assertEquals(Integer.valueOf(6), a.value());


        a.setValue(8);

        sm.restoreStateUntil(0);

        assertEquals(Integer.valueOf(6), a.value());


    }

    @ParameterizedTest
    @MethodSource("getStateManager")
    public void testCorrectOrderOfBackups(StateManager sm) {
        StateInt a = sm.makeStateInt(0);
        sm.saveState(); // level 0: a = 0
        a.setValue(1);
        a.setValue(2);
        sm.saveState(); // level 1: a = 2
        sm.restoreState(); // back to level 1: a = 2
        a.setValue(3);
        sm.restoreState(); // back to level 0: a = 0
        assertEquals(0, a.value());
    }




}
