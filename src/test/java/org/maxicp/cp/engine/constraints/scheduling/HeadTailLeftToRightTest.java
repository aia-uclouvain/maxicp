/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.api.Test;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class HeadTailLeftToRightTest {


    @Test
    public void testEdgeFinder1() {
        // example p26 of Petr Vilim's thesis
        HeadTailLeftToRight algo = new HeadTailLeftToRight(4);

        int[] startMin = new int[]{4, 13,  5,  5};
        int[] endMax = new int[] {30, 18, 13, 13};
        int[] duration = new int[]{4,  5,  3,  3}; // total duration = 16, there is thus an overload

        algo.filter(startMin, duration, endMax, 4);
        assertArrayEquals(new int [] {18, 13, 5, 5}, algo.startMin);
    }


    @Test
    public void testFilter1() {
        HeadTailLeftToRight algo = new HeadTailLeftToRight(3);

        int[] startMin = new int[]{0, 1, 3};
        int[] endMax = new int[]{14, 15, 13};
        int[] duration = new int[]{5, 5, 4};

        HeadTailLeftToRight.Outcome outcome = algo.filter(startMin, duration, endMax, 3);

        assertEquals(HeadTailLeftToRight.Outcome.CHANGE, outcome);
    }

    @Test
    public void testFilter2() {
        HeadTailLeftToRight algo = new HeadTailLeftToRight(3);

        int[] startMin = new int[]{0,  1, 3};
        int[] endMax = new int[] {14, 15, 13}; // total span = 15
        int[] duration = new int[]{5,  5, 6}; // total duration = 16, there is thus an overload

        HeadTailLeftToRight.Outcome outcome = algo.filter(startMin, duration, endMax, 3);
        assertEquals(HeadTailLeftToRight.Outcome.INCONSISTENCY, outcome);
    }


    @Test
    public void testFilter3() {
        HeadTailLeftToRight algo = new HeadTailLeftToRight(3);

        int[] startMin = new int[]{-5, -4, -3};
        int[] endMax = new int[]{14, 15, 13};
        int[] duration = new int[]{5, 5, 6};

        HeadTailLeftToRight.Outcome outcome = algo.filter(startMin, duration, endMax, 3);

        assertEquals(HeadTailLeftToRight.Outcome.NO_CHANGE, outcome);
    }


    @Test
    public void testFilter4() {
        HeadTailLeftToRight algo = new HeadTailLeftToRight(4);
        // this will only be detected by the edge finding
        int[] startMin = new int[]{0, 0, 1, 9};
        int[] endMax =   new int[]{30,9, 9,13};
        int[] duration = new int[]{4, 4, 4, 4};

        HeadTailLeftToRight.Outcome outcome = algo.filter(startMin, duration, endMax, 4);

        assertArrayEquals(new int[]{13, 0, 1, 9}, algo.startMin);

        assertEquals(HeadTailLeftToRight.Outcome.CHANGE, outcome);
    }
}
