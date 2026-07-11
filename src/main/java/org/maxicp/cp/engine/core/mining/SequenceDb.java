/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core.mining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable sequence database for sequential pattern mining.
 * <p>
 * A sequence database is a list of sequences, each sequence being an array
 * of positive symbol identifiers. Symbol 0 is reserved (epsilon, the empty symbol)
 * and never appears in a sequence.
 * <p>
 * The constructor precomputes the position structures needed by the PPIC
 * pseudo-projection kernel (Aoga, Guns, Schaus, ECML-PKDD 2016):
 * <ul>
 *     <li>{@code firstPos(s, a)}: first position (1-based) of symbol a in sequence s, 0 if absent;</li>
 *     <li>{@code lastPos(s, a)}: last position (1-based) of symbol a in sequence s, 0 if absent;</li>
 *     <li>{@code lastPosList(s)}: the last positions (1-based) of the distinct symbols of
 *         sequence s, sorted in decreasing order. Scanning this list until positions no
 *         longer exceed a projection point gives, in one pass, the set of symbols occurring
 *         in the projected suffix (used for support counting);</li>
 *     <li>{@code symbolSupport(a)}: number of sequences containing symbol a.</li>
 * </ul>
 */
public class SequenceDb {

    private final String name;
    private final int[][] sequences;
    private final int nSymbols;
    private final int maxSeqLength;

    private final int[][] firstPos; // firstPos[s][a] = first 1-based position of a in s, 0 if absent
    private final int[][] lastPos; // lastPos[s][a] = last 1-based position of a in s, 0 if absent
    private final int[][] lastPosList; // decreasing last positions of the distinct symbols of s
    private final int[] symbolSupport; // number of sequences containing each symbol

    /**
     * Creates a sequence database.
     *
     * @param name      the name of the database
     * @param sequences the sequences, each being an array of positive symbol ids
     */
    public SequenceDb(String name, int[][] sequences) {
        this.name = name;
        this.sequences = sequences;
        int maxSymbol = 0;
        int maxLen = 0;
        for (int[] seq : sequences) {
            maxLen = Math.max(maxLen, seq.length);
            for (int a : seq) {
                if (a <= 0)
                    throw new IllegalArgumentException("symbols must be positive (0 is reserved): " + a);
                maxSymbol = Math.max(maxSymbol, a);
            }
        }
        this.nSymbols = maxSymbol + 1;
        this.maxSeqLength = maxLen;
        int nSeqs = sequences.length;
        this.firstPos = new int[nSeqs][nSymbols];
        this.lastPos = new int[nSeqs][nSymbols];
        this.lastPosList = new int[nSeqs][];
        this.symbolSupport = new int[nSymbols];
        int[] tmp = new int[maxLen];
        for (int s = 0; s < nSeqs; s++) {
            int[] seq = sequences[s];
            // scan from the end: the first time a symbol is met is its last position
            int k = 0;
            for (int pos = seq.length - 1; pos >= 0; pos--) {
                int a = seq[pos];
                if (lastPos[s][a] == 0) {
                    lastPos[s][a] = pos + 1;
                    tmp[k++] = pos + 1;
                    symbolSupport[a]++;
                }
                firstPos[s][a] = pos + 1; // overwritten until the first occurrence remains
            }
            lastPosList[s] = Arrays.copyOf(tmp, k);
        }
    }

    /**
     * Reads a sequence database in SPMF format.
     * Each non-blank line is one sequence: whitespace-separated integer tokens,
     * where -1 terminates an itemset (itemsets are assumed to be singletons, so
     * -1 tokens are skipped) and -2 terminates the sequence.
     *
     * @param path the path to the file
     * @return the sequence database, named after the file base name
     */
    public static SequenceDb readSpmf(String path) {
        List<int[]> sequences = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path));
        } catch (IOException e) {
            throw new RuntimeException("cannot read " + path, e);
        }
        List<Integer> current = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank())
                continue;
            current.clear();
            for (String token : line.trim().split("\\s+")) {
                int v = Integer.parseInt(token);
                if (v == -2)
                    break;
                if (v == -1)
                    continue;
                if (v <= 0)
                    throw new IllegalArgumentException("invalid symbol " + v + " in " + path);
                current.add(v);
            }
            sequences.add(current.stream().mapToInt(Integer::intValue).toArray());
        }
        String fileName = Path.of(path).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String name = dot > 0 ? fileName.substring(0, dot) : fileName;
        return new SequenceDb(name, sequences.toArray(new int[0][]));
    }

    /**
     * Returns the name of the database.
     *
     * @return the name of the database
     */
    public String name() {
        return name;
    }

    /**
     * Returns the number of sequences.
     *
     * @return the number of sequences
     */
    public int nSeqs() {
        return sequences.length;
    }

    /**
     * Returns the number of symbols, i.e. the largest symbol id plus one.
     * Symbol 0 is reserved (epsilon), actual symbols range in {@code 1..nSymbols()-1}.
     *
     * @return the number of symbols
     */
    public int nSymbols() {
        return nSymbols;
    }

    /**
     * Returns a sequence of the database.
     * The returned array is the internal one and must not be modified.
     *
     * @param s the index of the sequence in {@code 0..nSeqs()-1}
     * @return the sequence
     */
    public int[] sequence(int s) {
        return sequences[s];
    }

    /**
     * Returns the length of the longest sequence.
     *
     * @return the length of the longest sequence, 0 if the database is empty
     */
    public int maxSeqLength() {
        return maxSeqLength;
    }

    /**
     * Returns the number of sequences containing a symbol.
     *
     * @param a a symbol in {@code 0..nSymbols()-1}
     * @return the number of sequences containing a
     */
    public int symbolSupport(int a) {
        return symbolSupport[a];
    }

    /**
     * Returns the first position of a symbol in a sequence.
     *
     * @param s the index of the sequence
     * @param a a symbol in {@code 0..nSymbols()-1}
     * @return the 1-based first position of a in sequence s, 0 if absent
     */
    public int firstPos(int s, int a) {
        return firstPos[s][a];
    }

    /**
     * Returns the last position of a symbol in a sequence.
     *
     * @param s the index of the sequence
     * @param a a symbol in {@code 0..nSymbols()-1}
     * @return the 1-based last position of a in sequence s, 0 if absent
     */
    public int lastPos(int s, int a) {
        return lastPos[s][a];
    }

    /**
     * Returns the last positions (1-based) of the distinct symbols of a sequence,
     * sorted in decreasing order.
     * The returned array is the internal one and must not be modified.
     *
     * @param s the index of the sequence
     * @return the decreasing list of last positions of the distinct symbols of s
     */
    public int[] lastPosList(int s) {
        return lastPosList[s];
    }

    @Override
    public String toString() {
        return name + "(" + nSeqs() + " sequences, " + (nSymbols - 1) + " symbols)";
    }
}
