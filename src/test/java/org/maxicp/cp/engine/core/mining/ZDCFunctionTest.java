/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZDCFunctionTest {

    @Test
    public void testZeroOnDiagonal() {
        // p / nTransP == n / nTransN => not discriminative => score 0
        assertEquals(0.0, ZDCFunction.CHI2.eval(2, 2, 4, 4), 1e-9);
        assertEquals(0.0, ZDCFunction.INF_GAIN.eval(2, 2, 4, 4), 1e-9);
        assertEquals(0.0, ZDCFunction.GINI.eval(2, 2, 4, 4), 1e-9);
        assertEquals(0.0, ZDCFunction.CHI2.eval(1, 2, 2, 4), 1e-9);
        assertEquals(0.0, ZDCFunction.INF_GAIN.eval(1, 2, 2, 4), 1e-9);
    }

    @Test
    public void testChi2PerfectSplit() {
        // a pattern covering all positives and no negative has chi2 = nTrans
        assertEquals(8.0, ZDCFunction.CHI2.eval(4, 0, 4, 4), 1e-9);
        assertEquals(10.0, ZDCFunction.CHI2.eval(3, 0, 3, 7), 1e-9);
    }

    @Test
    public void testSymmetryForBalancedClasses() {
        for (int p = 0; p <= 4; p++) {
            for (int n = 0; n <= 4; n++) {
                assertEquals(ZDCFunction.CHI2.eval(p, n, 4, 4), ZDCFunction.CHI2.eval(n, p, 4, 4), 1e-9);
                assertEquals(ZDCFunction.INF_GAIN.eval(p, n, 4, 4), ZDCFunction.INF_GAIN.eval(n, p, 4, 4), 1e-9);
                assertEquals(ZDCFunction.GINI.eval(p, n, 4, 4), ZDCFunction.GINI.eval(n, p, 4, 4), 1e-9);
            }
        }
    }

    @Test
    public void testNonNegativeAndBounded() {
        for (int p = 0; p <= 5; p++) {
            for (int n = 0; n <= 7; n++) {
                double chi2 = ZDCFunction.CHI2.eval(p, n, 5, 7);
                assertTrue(chi2 >= -1e-9, "chi2(" + p + "," + n + ") = " + chi2);
                assertTrue(chi2 <= 12 + 1e-9, "chi2 is at most nTrans");
                assertTrue(ZDCFunction.INF_GAIN.eval(p, n, 5, 7) >= -1e-9);
                assertTrue(ZDCFunction.GINI.eval(p, n, 5, 7) >= -1e-9);
            }
        }
    }

    @Test
    public void testScaled() {
        assertEquals(8000.0, ZDCFunction.CHI2.scaled(1000).eval(4, 0, 4, 4), 1e-9);
    }
}
