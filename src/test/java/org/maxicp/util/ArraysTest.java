/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.util;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.util.Arrays.*;


public class ArraysTest {

    @Test
    public void testArgMax() {
        int[] values = new int[]{1, 2, 3, 4, 5, 6, 9, 8, 8};
        assertEquals(6, argMax(values));
    }


    @Test
    public void testArgMin() {
        int[] values = new int[]{ 2, 3, 1, 4, 5, 6, 9, 8, 8};
        assertEquals(2, argMin(values));
    }

    @Test
    public void testSoftMax() {
        int [] values = new int[]{0,0,4,6};
        double [] proba = softMax(values);
        double denom = 2*Math.exp(0)+Math.exp(4)+Math.exp(6);
        assertEquals(Math.exp(0)/denom, proba[0], 1e-6);
        assertEquals(Math.exp(0)/denom, proba[1], 1e-6);
        assertEquals(Math.exp(4)/denom, proba[2], 1e-6);
        assertEquals(Math.exp(6)/denom, proba[3], 1e-6);
    }

}
