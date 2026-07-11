/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core.mining;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringJoiner;

/**
 * Test utilities for pattern mining: brute-force oracles and reference-solution parsing.
 * <p>
 * Patterns are canonically represented as strings of increasing item identifiers
 * separated by single spaces, the empty pattern being the empty string.
 */
public final class MiningTestUtils {

    private MiningTestUtils() {
    }

    /**
     * Parses a reference solution file with lines of the form {@code "1 2 3 #SUP: 4"},
     * as produced by external miners (Eclat, LCM, PrefixSpan, ...).
     *
     * @param path the path of the reference file
     * @return a map from canonical pattern string to support
     */
    public static Map<String, Integer> readSols(String path) {
        Map<String, Integer> sols = new HashMap<>();
        try {
            for (String line : Files.readAllLines(Path.of(path))) {
                String trimmed = line.trim();
                if (trimmed.isEmpty())
                    continue;
                String[] parts = trimmed.split("#SUP:");
                int[] items = parseItems(parts[0].trim());
                sols.put(canonical(items, items.length), Integer.parseInt(parts[1].trim()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sols;
    }

    private static int[] parseItems(String pattern) {
        if (pattern.isEmpty())
            return new int[0];
        String[] tokens = pattern.split("\\s+");
        int[] items = new int[tokens.length];
        for (int k = 0; k < tokens.length; k++) {
            items[k] = Integer.parseInt(tokens[k]);
        }
        return items;
    }

    /**
     * Returns the canonical string of the n first items of an array (sorted increasingly).
     *
     * @param items the items of the pattern
     * @param n     the number of items to consider
     * @return the canonical pattern string
     */
    public static String canonical(int[] items, int n) {
        int[] sorted = new int[n];
        System.arraycopy(items, 0, sorted, 0, n);
        java.util.Arrays.sort(sorted);
        StringJoiner joiner = new StringJoiner(" ");
        for (int item : sorted) {
            joiner.add(Integer.toString(item));
        }
        return joiner.toString();
    }

    /**
     * Computes the support of an itemset by scanning the database.
     *
     * @param db    the transaction database
     * @param items the items of the pattern
     * @param n     the number of items to consider in the array
     * @return the number of transactions containing all n first items
     */
    public static int support(TransactionDb db, int[] items, int n) {
        int support = 0;
        for (int t = 0; t < db.nTrans(); t++) {
            if (containsAll(db.transaction(t), items, n))
                support++;
        }
        return support;
    }

    private static boolean containsAll(int[] transaction, int[] items, int n) {
        outer:
        for (int k = 0; k < n; k++) {
            for (int item : transaction) {
                if (item == items[k])
                    continue outer;
            }
            return false;
        }
        return true;
    }

    /**
     * Enumerates all frequent itemsets (including the empty one) by brute force,
     * over the items {@code 1..nItems()-1}.
     *
     * @param db    the transaction database
     * @param theta the minimum support threshold
     * @return a map from canonical pattern string to support
     */
    public static Map<String, Integer> frequentItemsets(TransactionDb db, int theta) {
        Map<String, Integer> sols = new HashMap<>();
        int n = db.nItems() - 1; // items 1..nItems-1
        int[] items = new int[n];
        for (int mask = 0; mask < (1 << n); mask++) {
            int size = 0;
            for (int k = 0; k < n; k++) {
                if ((mask & (1 << k)) != 0)
                    items[size++] = k + 1;
            }
            int support = support(db, items, size);
            if (support >= theta)
                sols.put(canonical(items, size), support);
        }
        return sols;
    }

    /**
     * Enumerates all closed frequent itemsets (including the empty one if closed)
     * by brute force, over the items {@code 1..nItems()-1}.
     *
     * @param db    the transaction database
     * @param theta the minimum support threshold
     * @return a map from canonical pattern string to support
     */
    public static Map<String, Integer> closedFrequentItemsets(TransactionDb db, int theta) {
        Map<String, Integer> frequent = frequentItemsets(db, theta);
        Map<String, Integer> closed = new HashMap<>();
        for (Map.Entry<String, Integer> entry : frequent.entrySet()) {
            int[] items = parseItems(entry.getKey());
            int[] extended = java.util.Arrays.copyOf(items, items.length + 1);
            boolean isClosed = true;
            for (int j = 1; j < db.nItems() && isClosed; j++) {
                if (contains(items, j))
                    continue;
                extended[items.length] = j;
                if (support(db, extended, extended.length) == entry.getValue())
                    isClosed = false;
            }
            if (isClosed)
                closed.put(entry.getKey(), entry.getValue());
        }
        return closed;
    }

    private static boolean contains(int[] items, int item) {
        for (int i : items) {
            if (i == item)
                return true;
        }
        return false;
    }

    /**
     * Generates a random transaction database over items {@code 1..nItems-1}.
     *
     * @param rand    the random source
     * @param nTrans  the number of transactions
     * @param nItems  the number of item identifiers (items range over 1..nItems-1)
     * @param density the probability that an item belongs to a transaction
     * @return the random database
     */
    public static TransactionDb randomDb(Random rand, int nTrans, int nItems, double density) {
        int[][] transactions = new int[nTrans][];
        for (int t = 0; t < nTrans; t++) {
            List<Integer> items = new ArrayList<>();
            for (int i = 1; i < nItems; i++) {
                if (rand.nextDouble() < density)
                    items.add(i);
            }
            transactions[t] = items.stream().mapToInt(Integer::intValue).toArray();
        }
        return new TransactionDb("random", transactions);
    }

    /**
     * Generates a random labeled transaction database with labels in {0, 1};
     * at least one transaction of each class is guaranteed ({@code nTrans >= 2}).
     *
     * @param rand    the random source
     * @param nTrans  the number of transactions, at least 2
     * @param nItems  the number of item identifiers
     * @param density the probability that an item belongs to a transaction
     * @return the random labeled database
     */
    public static TransactionDb randomLabeledDb(Random rand, int nTrans, int nItems, double density) {
        TransactionDb unlabeled = randomDb(rand, nTrans, nItems, density);
        int[][] transactions = new int[nTrans][];
        int[] labels = new int[nTrans];
        for (int t = 0; t < nTrans; t++) {
            transactions[t] = unlabeled.transaction(t);
            labels[t] = t == 0 ? 1 : t == 1 ? 0 : rand.nextInt(2);
        }
        return new TransactionDb("randomLabeled", transactions, labels);
    }
}
