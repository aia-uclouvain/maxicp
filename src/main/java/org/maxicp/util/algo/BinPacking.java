package org.maxicp.util.algo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * This class provides algorithms for the Bin Packing.
 * @author pschaus
 */
public class BinPacking {

    /**
     * Computes the bin index for each item using the First-Fit Decreasing heuristic.
     * The number of bins used is {@code Arrays.stream(result).max().orElse(0) + 1}.
     *
     * @param weights an array of item weights
     * @param capacity the capacity of each bin
     * @return an array where {@code result[i]} represents the bin index of item {@code i}
     * @throws IllegalArgumentException if any item weight is negative or exceeds the bin capacity
     */
    public static int[] firstFitDecreasing(int[] weights, int capacity) {
        // Validate item weights
        if (Arrays.stream(weights).anyMatch(w -> w > capacity || w < 0)) {
            throw new IllegalArgumentException("Item's weight should be less than capacity and non-negative");
        }

        // Pair each weight with its original index and sort in decreasing order
        Integer[] indices = IntStream.range(0, weights.length)
                .boxed()
                .sorted(Comparator.comparingInt(i -> -weights[i])) // Sort by weight descending
                .toArray(Integer[]::new);

        // Initialize arrays for bin assignment and available capacity
        int[] positions = new int[weights.length]; // Bin assignment for each item
        int[] slack = new int[weights.length]; // Remaining space in bins
        Arrays.fill(slack, capacity); // Initially, all bins have full capacity

        // Assign items to bins using first-fit decreasing
        for (int i : indices) {
            int w = weights[i];
            int j = 0;

            // Find first bin that can accommodate the item
            while (j < weights.length && slack[j] < w) {
                j++;
            }

            // Place item in bin j
            slack[j] -= w;
            positions[i] = j;
        }

        return positions;
    }


    /**
     * Lower-Bound introduced in:
     *
     * "Capacitated vehicle routing on trees."
     * Labbe Martine, Gilbert Laporte, and Helene Mercure.
     * Operations Research 39.4 (1991): 616-622.
     *
     * @param w an array of decreasing positive weights
     * @param c the capacity
     * @return the computed lower bound
     */
    public static int labbeLB(int[] w, int c) {
        int n = w.length;
        // Compute the number of items > c/2
        int ind1 = 0;
        while (ind1 < w.length && w[ind1] > c / 2) {
            ind1++;
        }
        int L3 = ind1;
        // Place items with c/3 < w(i) <= c/2
        int ind2 = ind1;
        while (ind2 < w.length && w[ind2] > c / 3) {
            ind2++;
        }
        int ind3 = ind2 - 1;
        int ind4 = 0;
        while (ind4 < ind1 && ind3 >= ind1) {
            // Place item at index ind3 in the subsequence [ind4, ind1-1]
            do {
                ind4++;
            } while (ind4 < ind1 && c - w[ind4 - 1] < w[ind3]);
            if (ind4 <= ind1) {
                ind3--; // Could we place the item?
            }
        }
        int H = ind3 - (ind1 - 1);
        L3 += (H + 1) / 2;
        // Computation of p(v)
        int a = 0;
        int b = n - 1;
        int v = 0;
        int sum_ab = 0;
        for (int i = a; i < n; i++) {
            sum_ab += w[i];
        }
        int p_v = 0;
        /*
         * Invariant:
         *
         *   . > c-v     |a          c-v >= . >= v           b|  v > .
         * +-------------|----------------|-------------------|----------0+
         *                  . > c/2      e|      c/2 >= .
         *
         * sum_ab = sum_{i in [a..b]} w[i]
         * v <= c/3
         * v = w[b]
         * b >= a
         */
        int e = w.length - 1;
        while (e >= 0 && w[e] <= c / 2) {
            e--;
        }
        while (b >= a && v <= c / 3) {
            int tmp = (int) Math.ceil((double) sum_ab / c) - Math.max(0, e - a + 1) - (H + 1) / 2;
            p_v = Math.max(p_v, tmp);
            // Reduce b
            while (b >= a && w[b] == v) {
                sum_ab -= w[b];
                b--;
            }
            if (b >= a) {
                v = w[b];
                // Increase a
                while (b >= a && w[a] > c - v) {
                    sum_ab -= w[a];
                    a++;
                }
            }
        }
        L3 += p_v;
        return L3;
    }
}
