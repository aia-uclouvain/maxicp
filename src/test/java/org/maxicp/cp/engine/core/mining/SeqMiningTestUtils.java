/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core.mining;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Brute-force helpers for sequence mining tests.
 */
public class SeqMiningTestUtils {

    /**
     * Reads a solution file with lines of the form {@code "a b c #SUP: n"}.
     * The key of the returned map is the space-joined symbol sequence, in order.
     */
    public static Map<String, Integer> readSols(String path) {
        Map<String, Integer> sols = new HashMap<>();
        try {
            for (String line : Files.readAllLines(Path.of(path))) {
                if (line.isBlank())
                    continue;
                int sep = line.indexOf("#SUP:");
                String pattern = line.substring(0, sep).trim().replaceAll("\\s+", " ");
                int support = Integer.parseInt(line.substring(sep + "#SUP:".length()).trim());
                sols.put(pattern, support);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sols;
    }

    /**
     * Returns the number of database sequences containing {@code pattern[0..len)}
     * as a subsequence.
     */
    public static int subsequenceSupport(SequenceDb db, int[] pattern, int len) {
        int count = 0;
        for (int s = 0; s < db.nSeqs(); s++)
            if (isSubsequence(db.sequence(s), pattern, len))
                count++;
        return count;
    }

    private static boolean isSubsequence(int[] seq, int[] pattern, int len) {
        int j = 0;
        for (int i = 0; i < seq.length && j < len; i++)
            if (seq[i] == pattern[j])
                j++;
        return j == len;
    }

    /**
     * Brute-force enumeration of all the sequences of length at most maxLen over
     * the symbols {@code 1..db.nSymbols()-1} with support at least theta,
     * including the empty sequence (key {@code ""}) if it is frequent.
     * Keys are space-joined symbol sequences, in order.
     */
    public static Map<String, Integer> frequentSequences(SequenceDb db, int theta, int maxLen) {
        Map<String, Integer> res = new HashMap<>();
        enumerate(db, theta, maxLen, new int[maxLen], 0, res);
        return res;
    }

    private static void enumerate(SequenceDb db, int theta, int maxLen, int[] pattern, int len, Map<String, Integer> res) {
        int support = subsequenceSupport(db, pattern, len);
        if (support < theta)
            return; // no extension can be frequent (anti-monotonicity)
        res.put(join(pattern, len), support);
        if (len == maxLen)
            return;
        for (int a = 1; a < db.nSymbols(); a++) {
            pattern[len] = a;
            enumerate(db, theta, maxLen, pattern, len + 1, res);
        }
    }

    /**
     * Space-joins the first len symbols of a pattern, in order.
     */
    public static String join(int[] pattern, int len) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < len; j++) {
            if (j > 0)
                sb.append(' ');
            sb.append(pattern[j]);
        }
        return sb.toString();
    }

    /**
     * Random sequence database with nSeqs sequences of lengths in {@code 0..maxLen}
     * and symbols uniform in {@code 1..nSymbols-1}.
     */
    public static SequenceDb randomDb(Random rand, int nSeqs, int nSymbols, int maxLen) {
        int[][] sequences = new int[nSeqs][];
        for (int s = 0; s < nSeqs; s++) {
            int len = rand.nextInt(maxLen + 1);
            sequences[s] = new int[len];
            for (int i = 0; i < len; i++)
                sequences[s][i] = 1 + rand.nextInt(nSymbols - 1);
        }
        return new SequenceDb("random", sequences);
    }
}
