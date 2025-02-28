/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.api.Test;

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

        int[] startMin = new int[]{0, 1, 3};
        int[] endMax = new int[]{14, 15, 13}; // total span = 15
        int[] duration = new int[]{5, 5, 6}; // total duration = 16, there is thus an overload

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
}
