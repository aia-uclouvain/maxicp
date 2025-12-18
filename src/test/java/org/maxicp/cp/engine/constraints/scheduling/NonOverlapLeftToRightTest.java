/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.api.Test;
import org.maxicp.util.exception.InconsistencyException;

import static org.junit.jupiter.api.Assertions.*;

public class NonOverlapLeftToRightTest {

    @Test
    public void testOverloadChecker0() {
        NoOverlapLeftToRight algo = new NoOverlapLeftToRight(3);

        int[] startMin = new int[]{2, 0, 1};
        int[] endMax = new int[]{3, 2, 2};
        int[] duration = new int[]{1, 1, 1};

        algo.update(startMin, duration, endMax, 3);
        assertTrue(algo.overLoadChecker()); // should not fail
    }

    @Test
    public void testOverloadChecker1() {
        NoOverlapLeftToRight algo = new NoOverlapLeftToRight(3);

        int[] startMin = new int[]{0, 1, 3};
        int[] endMax = new int[]{14, 15, 13}; // total span = 15
        int[] duration = new int[]{5, 5, 6}; // total duration = 16, there is thus an overload

        algo.update(startMin, duration, endMax, 3);
        assertFalse(algo.overLoadChecker());

        endMax[1] = 16; // now it should fit

        algo.update(startMin, duration, endMax, 3);
        assertTrue(algo.overLoadChecker());
    }

    @Test
    public void testDetectablePrecedence() {
        NoOverlapLeftToRight algo = new NoOverlapLeftToRight(3);

        int[] startMin = new int[]{0, 1, 8};
        int[] endMax = new int[]{14, 15, 18};
        int[] duration = new int[]{5, 5, 3};

        algo.update(startMin, duration, endMax, 3);
        assertTrue(algo.detectablePrecedence());

        assertEquals(0, algo.startMin[0]); // not changed
        assertEquals(1, algo.startMin[1]); // not changed
        assertEquals(10, algo.startMin[2]); // pushed from 8 to 10
    }

    @Test
    public void testNotLast() {
        NoOverlapLeftToRight algo = new NoOverlapLeftToRight(3);

        int[] startMin = new int[]{0, 1, 3};
        int[] endMax = new int[]{14, 15, 13};
        int[] duration = new int[]{5, 5, 4};

        algo.update(startMin, duration, endMax, 3);
        assertTrue(algo.notLast()); // some propagation possible

        assertEquals(0, algo.startMin[0]); // not changed
        assertEquals(1, algo.startMin[1]); // not changed
        assertEquals(3, algo.startMin[2]); // not changed

        assertEquals(10, algo.endMax[0]); // this task cannot be last
        assertEquals(15, algo.endMax[1]);
        assertEquals(10, algo.endMax[2]); // this task cannot be last

        endMax[0] = 15; // now first task can be last

        algo.update(startMin, duration, endMax, 3);
        assertTrue(algo.notLast()); // some propagation possible

        assertEquals(0, algo.startMin[0]); // not changed
        assertEquals(1, algo.startMin[1]); // not changed
        assertEquals(3, algo.startMin[2]); // not changed

        assertEquals(15, algo.endMax[0]);
        assertEquals(15, algo.endMax[1]);
        assertEquals(10, algo.endMax[2]); // this task cannot be last
    }


    @Test
    public void testEdgeFinder1() {
        // example p26 of Petr Vilim's thesis
        NoOverlapLeftToRight algo = new NoOverlapLeftToRight(4);

        int[] startMin = new int[]{4, 13,  5,  5};
        int[] endMax = new int[] {30, 18, 13, 13};
        int[] duration = new int[]{4,  5,  3,  3}; // total duration = 16, there is thus an overload

        algo.update(startMin, duration, endMax, 4);
        algo.edgeFinding();
        assertArrayEquals(new int [] {18, 13, 5, 5}, algo.startMin);
    }

    @Test
    public void testEdgeFinder2() {
        NoOverlapLeftToRight algo = new NoOverlapLeftToRight(8);

        int[] startMin = new int[]{45, 128, 130,  0, 38, 50, 70, 33};
        int[] endMax = new int [] {56, 144, 147, 30, 51, 69, 74, 74};
        int[] duration = new int[]{ 5,   9,   5,  7,  7, 13, 12, 12};

        algo.update(startMin, duration, endMax, 8);
        boolean failed = false;
        try {
            algo.edgeFinding();
        } catch (InconsistencyException e) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void testEdgeFinder3() {
        NoOverlapLeftToRight algo = new NoOverlapLeftToRight(6);

        int[] startMin = new int[]{4, 0, 9, 15, 20, 21};
        int[] endMax = new int[] {32, 27, 22, 43, 38, 36};
        int[] duration = new int[]{6, 8, 4, 5, 8, 8};

        algo.update(startMin, duration, endMax, 6);
        boolean failed = false;
        try {
            algo.edgeFinding();
        } catch (InconsistencyException e) {
            failed = true;
        }
        assertFalse(failed);
        assertArrayEquals( new int[]{4, 0, 9, 36, 20, 21}, algo.startMin);
    }

    @Test
    public void testFilter1() {
        NoOverlapLeftToRight algo = new NoOverlapLeftToRight(3);

        int[] startMin = new int[]{0, 1, 3};
        int[] endMax = new int[]{14, 15, 13};
        int[] duration = new int[]{5, 5, 4};

        NoOverlapLeftToRight.Outcome outcome = algo.filter(startMin, duration, endMax, 3);

        assertEquals(NoOverlapLeftToRight.Outcome.CHANGE, outcome);
    }

    @Test
    public void testFilter2() {
        NoOverlapLeftToRight algo = new NoOverlapLeftToRight(3);

        int[] startMin = new int[]{0,  1, 3};
        int[] endMax = new int[] {14, 15, 13}; // total span = 15
        int[] duration = new int[]{5,  5, 6}; // total duration = 16, there is thus an overload

        NoOverlapLeftToRight.Outcome outcome = algo.filter(startMin, duration, endMax, 3);

        assertEquals(NoOverlapLeftToRight.Outcome.INCONSISTENCY, outcome);
    }


    @Test
    public void testFilter3() {
        NoOverlapLeftToRight algo = new NoOverlapLeftToRight(3);

        int[] startMin = new int[]{-5, -4, -3};
        int[] endMax = new int[]{14, 15, 13};
        int[] duration = new int[]{5, 5, 6};

        NoOverlapLeftToRight.Outcome outcome = algo.filter(startMin, duration, endMax, 3);

        assertEquals(NoOverlapLeftToRight.Outcome.NO_CHANGE, outcome);
    }


    @Test
    public void testFilter4() {
        NoOverlapLeftToRight algo = new NoOverlapLeftToRight(4);
        // this will only be detected by the edge finding
        int[] startMin = new int[]{0, 0, 1, 9};
        int[] endMax =   new int[]{30,9, 9,13};
        int[] duration = new int[]{4, 4, 4, 4};

        NoOverlapLeftToRight.Outcome outcome = algo.filter(startMin, duration, endMax, 4);

        assertArrayEquals(new int[]{13, 0, 1, 9}, algo.startMin);

        assertEquals(NoOverlapLeftToRight.Outcome.CHANGE, outcome);
    }
}
