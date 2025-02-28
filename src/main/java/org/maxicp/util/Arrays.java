/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.util;

public class Arrays {

    public static int argMax(int [] values) {
        int max = Integer.MIN_VALUE;
        int argMax = -1;
        for(int i = 0; i < values.length; ++i) {
            if(values[i] > max) {
                max = values[i];
                argMax = i;
            }
        }
        return argMax;
    }

    public static int argMin(int [] values) {
        int min = Integer.MAX_VALUE;
        int argMin = -1;
        for(int i = 0; i < values.length; ++i) {
            if(values[i] < min) {
                min = values[i];
                argMin = i;
            }
        }
        return argMin;
    }

    public static int argProb(double [] proba) {
        double r = Math.random();
        double sum = 0;
        for(int i = 0; i < proba.length; i++) {
            sum += proba[i];
            if (sum >= r) {
                return i;
            }
        }
        return proba.length - 1;
    }

    public static double [] softMax(int [] values) {
        double [] res = new double[values.length];
        double sum = 0;
        for(int i = 0; i < values.length; ++i) {
            res[i] = Math.exp(values[i]);
            sum += res[i];
        }
        for(int i = 0; i < values.length; ++i) {
            res[i] /= sum;
        }
        return res;
    }

    public static int argSoftMax(int [] values) {
        double [] proba = softMax(values);
        return argProb(proba);
    }

    public static int max(int[][] values) {
        int max = Integer.MIN_VALUE;
        for (int[] row : values)
            for (int value: row)
                max = Math.max(max, value);
        return max;
    }

}
