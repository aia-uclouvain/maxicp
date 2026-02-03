/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.api.Test;
import org.maxicp.util.exception.InconsistencyException;

import static org.junit.jupiter.api.Assertions.*;

public class NonOverlapBCLeftToRightTest {

    @Test
    public void testOverloadChecker0() {
        NoOverlapBCLeftToRight algo = new NoOverlapBCLeftToRight(3);

        int[] startMin = new int[]{2, 0, 1};
        int[] endMax = new int[]{3, 2, 2};
        int[] duration = new int[]{1, 1, 1};

        assertEquals(NoOverlapBCLeftToRight.Outcome.NO_CHANGE,
                algo.filter(startMin, duration, endMax, 3));
    }


    @Test
    public void testOverloadChecker1() {
        NoOverlapBCLeftToRight algo = new NoOverlapBCLeftToRight(3);

        int[] startMin = new int[]{0, 1, 3};
        int[] endMax = new int[]{14, 15, 13}; // total span = 15
        int[] duration = new int[]{5, 5, 6}; // total duration = 16, there is thus an overload

        assertEquals(NoOverlapBCLeftToRight.Outcome.INCONSISTENCY,
                algo.filter(startMin, duration, endMax, 3));


        endMax[1] = 16; // now it should fit

        assertEquals(NoOverlapBCLeftToRight.Outcome.CHANGE,
                algo.filter(startMin, duration, endMax, 3));

        assertArrayEquals(new int [] {0, 11, 5}, algo.startMin);
    }


    @Test
    public void testDetectablePrecedence() {
        NoOverlapBCLeftToRight algo = new NoOverlapBCLeftToRight(3);

        int[] startMin = new int[]{0, 1, 8};
        int[] endMax = new int[]{14, 15, 18};
        int[] duration = new int[]{5, 5, 3};

        assertEquals(NoOverlapBCLeftToRight.Outcome.CHANGE,
                algo.filter(startMin, duration, endMax, 3));

        assertEquals(0, algo.startMin[0]); // not changed
        assertEquals(1, algo.startMin[1]); // not changed
        assertEquals(10, algo.startMin[2]); // pushed from 8 to 10
    }

    @Test
    public void testEdgeFinder1() {
        // example p26 of Petr Vilim's thesis
        NoOverlapBCLeftToRight algo = new NoOverlapBCLeftToRight(4);

        int[] startMin = new int[]{4, 13,  5,  5};
        int[] endMax = new int[] {30, 18, 13, 13};
        int[] duration = new int[]{4,  5,  3,  3}; // total duration = 16, there is thus an overload

        assertEquals(NoOverlapBCLeftToRight.Outcome.CHANGE,
                algo.filter(startMin, duration, endMax, 4));
        assertArrayEquals(new int [] {18, 13, 5, 5}, algo.startMin);
    }

    @Test
    public void testEdgeFinder2() {
        NoOverlapBCLeftToRight algo = new NoOverlapBCLeftToRight(8);

        int[] startMin = new int[]{45, 128, 130,  0, 38, 50, 70, 33};
        int[] endMax = new int [] {56, 144, 147, 30, 51, 69, 74, 74};
        int[] duration = new int[]{ 5,   9,   5,  7,  7, 13, 12, 12};

        assertEquals(NoOverlapBCLeftToRight.Outcome.INCONSISTENCY,
                algo.filter(startMin, duration, endMax, 8));
    }

    @Test
    public void testEdgeFinder3() {
        NoOverlapBCLeftToRight algo = new NoOverlapBCLeftToRight(6);

        int[] startMin = new int[]{4, 0, 9, 15, 20, 21};
        int[] endMax = new int[] {32, 27, 22, 43, 38, 36};
        int[] duration = new int[]{6, 8, 4, 5, 8, 8};

        assertEquals(NoOverlapBCLeftToRight.Outcome.CHANGE,
                algo.filter(startMin, duration, endMax, 6));

        assertArrayEquals( new int[]{4, 0, 9, 36, 20, 21}, algo.startMin);
    }

    @Test
    public void testFilter1() {
        NoOverlapBCLeftToRight algo = new NoOverlapBCLeftToRight(3);

        int[] startMin = new int[]{0, 1, 3};
        int[] endMax = new int[]{14, 15, 13};
        int[] duration = new int[]{5, 5, 4};

        assertEquals(NoOverlapBCLeftToRight.Outcome.CHANGE,
                algo.filter(startMin, duration, endMax, 3));
    }

    @Test
    public void testFilter2() {
        NoOverlapBCLeftToRight algo = new NoOverlapBCLeftToRight(3);

        int[] startMin = new int[]{0,  1, 3};
        int[] endMax = new int[] {14, 15, 13}; // total span = 15
        int[] duration = new int[]{5,  5, 6}; // total duration = 16, there is thus an overload

        assertEquals(NoOverlapBCLeftToRight.Outcome.INCONSISTENCY,
                algo.filter(startMin, duration, endMax, 3));
    }


    @Test
    public void testFilter3() {
        NoOverlapBCLeftToRight algo = new NoOverlapBCLeftToRight(3);

        int[] startMin = new int[]{-5, -4, -3};
        int[] endMax = new int[]{14, 15, 13};
        int[] duration = new int[]{5, 5, 6};

        assertEquals(NoOverlapBCLeftToRight.Outcome.NO_CHANGE,
                algo.filter(startMin, duration, endMax, 3));
    }


    @Test
    public void testFilter4() {
        NoOverlapBCLeftToRight algo = new NoOverlapBCLeftToRight(4);
        // this will only be detected by the edge finding
        int[] startMin = new int[]{0, 0, 1, 9};
        int[] endMax =   new int[]{30,9, 9,13};
        int[] duration = new int[]{4, 4, 4, 4};

        assertEquals(NoOverlapBCLeftToRight.Outcome.CHANGE,
                algo.filter(startMin, duration, endMax, 4));

        assertArrayEquals(new int[]{13, 0, 1, 9}, algo.startMin);

    }


    @Test
    public void testFilter5() {
        NoOverlapBCLeftToRight algo = new NoOverlapBCLeftToRight(3);
        // this will only be detected by the edge finding
        int[] startMin = new int[]{-3, -3, -3};
        int[] endMax = new int[]{-2, 0, 0};
        int[] duration = new int[]{1, 1, 1};

        assertEquals(NoOverlapBCLeftToRight.Outcome.CHANGE,
                algo.filter(startMin, duration, endMax, 3));

        assertArrayEquals(new int[]{-3,-2,-2}, algo.startMin);

    }

}
