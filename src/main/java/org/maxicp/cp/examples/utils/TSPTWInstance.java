/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.utils;

import org.maxicp.util.io.InputReader;

import java.util.Arrays;

public class TSPTWInstance {

    public final int n;
    public final int[][] distMatrix;
    public final int[] earliest, latest;
    public int horizon = Integer.MIN_VALUE;

    public TSPTWInstance(String file) {
        InputReader reader = new InputReader(file);
        n = reader.getInt();
        distMatrix = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                distMatrix[i][j] = reader.getInt();
            }
        }
        earliest = new int[n];
        latest = new int[n];

        for (int i = 0; i < n; i++) {
            earliest[i] = reader.getInt();
            latest[i] = reader.getInt();
            horizon = Math.max(horizon, latest[i] + 1);
        }
    }

    private TSPTWInstance(int[][] distMatrix, int[] E, int[] L) {
        n = E.length;
        this.earliest = E;
        this.latest = L;
        this.distMatrix = distMatrix;
        for (int i = 0; i < n; i++) {
            horizon = Math.max(horizon, L[i] + 1);
        }
    }

    @Override
    public String toString() {
        return "Instance{" +
                "n=" + n + "\n" +
                ", distMatrix=" + Arrays.deepToString(distMatrix) + "\n" +
                ", E=" + Arrays.toString(earliest) + "\n" +
                ", L=" + Arrays.toString(latest) + "\n" +
                ", horizon=" + horizon +
                '}';
    }
}
