/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.util;

public class NumberUtils {

    public static boolean isPerfectSquare(int n) {
        if (n < 0)
            return false;
        switch((int)(n & 0x3F))
        {
            case 0x00: case 0x01: case 0x04: case 0x09: case 0x10: case 0x11:
            case 0x19: case 0x21: case 0x24: case 0x29: case 0x31: case 0x39:
            int sqrt;
            if (n < 410881L) {
                //John Carmack hack, converted to Java.
                // See: http://www.codemaestro.com/reviews/9
                int i;
                float x2, y;

                x2 = n * 0.5F;
                y  = n;
                i  = Float.floatToRawIntBits(y);
                i  = 0x5f3759df - ( i >> 1 );
                y  = Float.intBitsToFloat(i);
                y  = y * ( 1.5F - ( x2 * y * y ) );

                sqrt = (int)(1.0F/y);
            }
            else
            {
                //Carmack hack gives incorrect answer for n >= 410881.
                sqrt = (int) Math.sqrt(n);
            }
            return sqrt*sqrt == n;

            default:
                return false;
        }
    }

    public static boolean negativeProduct(int v1, int v2) {
        return (v2 < 0 ^ v1 < 0) && (v1 != 0 && v2 != 0);
    }

    public static boolean positiveProduct(int v1, int v2) {
        return (v2 > 0 && v1 > 0) || (v1 < 0 && v2 < 0);
    }

    /**
     * @param v1
     * @param v2 != 0
     * @return ceil(v1/v2)
     */
    public static int ceilDiv(int v1, int v2) {
        return v1 / v2 + (((v1%v2 != 0) && positiveProduct(v1,v2)) ? 1 : 0);
    }

    /**
     * @param v1
     * @param v2 != 0
     * @return floor(v1/v2)
     */
    public static int floorDiv(int v1, int v2) {
        return v1 / v2 - (((v1%v2 != 0) && negativeProduct(v1,v2)) ? 1 : 0);
    }

    /**
     * @param c != 0
     * @param vals different from 0
     * @return min(ceil(c/v)) with v in vals
     */
    public static int minCeilDiv(int c, int ... vals)  {
        assert(vals.length > 0);
        assert(vals[0] != 0);
        int res = Integer.MAX_VALUE;
        for (int i = 0; i< vals.length; i++) {
            assert (vals[i] != 0);
            int tmp = ceilDiv(c, vals[i]);
            if (tmp < res) res = tmp;
        }
        return res;
    }

    /**
     * @param c != 0
     * @param vals different from 0
     * @return max(floor(c/v)) with v in vals
     */
    public static int maxFloorDiv(int c, int ... vals)  {
        assert(vals.length > 0);
        assert(vals[0] != 0);
        int res = Integer.MIN_VALUE;
        for (int i = 0; i< vals.length; i++) {
            assert (vals[i] != 0);
            int tmp = floorDiv(c, vals[i]);
            if (tmp > res) res = tmp;
        }
        return res;
    }


    /**
     * @param a
     * @param b
     * @return true if a*b generates an overflow
     */
    public static boolean overFlowMul(int a, int b) {
        if (a == 0 || b == 0) return false;
        return (a * b) / b != a;
    }

    /**
     *
     * @param a
     * @param b
     * @return a*b (rounded to Integer.MAX_VALUE /Integer.MIN_VALUE in case of overflow)
     */
    public static int safeMul(int a, int b) {
        if (overFlowMul(a, b)) {
            System.out.println("warning: overflow multiplying "+a+"*"+b);
            if ((a > 0 && b > 0) || (a < 0 && b < 0)) {
                return Integer.MAX_VALUE;
            } else {
                return Integer.MIN_VALUE;
            }
        } else {
            return a*b;
        }
    }

}
