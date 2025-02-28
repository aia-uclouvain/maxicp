package org.maxicp.util.algo;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class BinPackingTest {
    /**
     * Generates an instance where `n` bins are completely filled with capacity `c`
     * using random items.
     *
     * @param c the capacity of each bin
     * @param n the number of bins
     * @return an array of items sorted in decreasing order
     */
    public static int[] generateInstance1(int c, int n) {
        Random rand = new Random(0);
        List<Integer> itemsList = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            int l = 0;
            List<Integer> binItems = new ArrayList<>();
            while (l < c) {
                int item = Math.min(3 + rand.nextInt(c), c - l);
                l += item;
                binItems.add(item);
            }
            itemsList.addAll(binItems);
        }

        // Convert to array and sort in decreasing order
        return itemsList.stream().mapToInt(Integer::intValue).boxed()
                .sorted((a, b) -> Integer.compare(b, a))
                .mapToInt(Integer::intValue).toArray();
    }

    /**
     * Tests the first-fit decreasing heuristic, ensuring it returns the correct upper bound.
     */
    @RepeatedTest(400)
    public void testFirstFit() {
        int[] items = generateInstance1(10, 100);
        int[] bins = BinPacking.firstFitDecreasing(items, 10);
        int ub = Arrays.stream(bins).max().orElse(0) + 1;

        assertThat(ub).isEqualTo(100);
    }

    /**
     * Tests the lower bound function (labbeLB) ensuring it returns the expected value.
     */
    @RepeatedTest(400)
    public void testLowerBound() {
        int[] items = generateInstance1(10, 100);
        int lb = BinPacking.labbeLB(items, 10);

        assertThat(lb).isEqualTo(100);
    }

    // Helper function for the test cases
    static void test(int[] items) {
        int lb = BinPacking.labbeLB(items, 10);
        int[] bins = BinPacking.firstFitDecreasing(items, 10);
        int ub = Arrays.stream(bins).max().orElse(0) + 1;

        assertThat(lb).isLessThanOrEqualTo(ub);
        assertThat(lb).isGreaterThanOrEqualTo(ub * 3 / 4);
    };

    /**
     * Tests that the lower bound is always <= first-fit decreasing upper bound
     * and >= 3/4 of the upper bound.
     */
    @Test
    public void testLowerBoundComparedToFirstFit() {
        Random rand = new Random();
        // Run test cases with different item distributions
        for (int i = 0; i < 1000; i++) {
            test(IntStream.generate(() -> rand.nextInt(7) + 3).limit(500)
                    .boxed().sorted((a, b) -> Integer.compare(b, a)).mapToInt(Integer::intValue).toArray());
            test(IntStream.generate(() -> rand.nextInt(3) + 1).limit(500)
                    .boxed().sorted((a, b) -> Integer.compare(b, a)).mapToInt(Integer::intValue).toArray());
            test(IntStream.generate(() -> rand.nextInt(4) + 5).limit(500)
                    .boxed().sorted((a, b) -> Integer.compare(b, a)).mapToInt(Integer::intValue).toArray());
            test(IntStream.generate(() -> rand.nextInt(10) + 1).limit(10)
                    .boxed().sorted((a, b) -> Integer.compare(b, a)).mapToInt(Integer::intValue).toArray());
        }
    }

}