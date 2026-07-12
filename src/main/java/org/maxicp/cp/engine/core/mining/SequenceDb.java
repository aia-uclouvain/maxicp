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
 * A database can optionally carry one timestamp per event (see
 * {@link #SequenceDb(String, int[][], int[][])} and {@link #readSpmfWithTime(String)}),
 * which is required by the time-constrained pattern kernel (PPICt, Aoga, Guns,
 * Schaus, Constraints 22:548-570, 2017). Timestamps are non-decreasing within
 * a sequence, as assumed by PPICt.
 */
public class SequenceDb {

    private final String name;
    private final int[][] sequences;
    private final int[][] times; // times[s][j] = timestamp of event j of sequence s, null if untimed
    private final int nSymbols;
    private final int maxSeqLength;

    private final int[][] firstPos; // firstPos[s][a] = first 1-based position of a in s, 0 if absent
    private final int[][] lastPos; // lastPos[s][a] = last 1-based position of a in s, 0 if absent
    private final int[][] lastPosList; // decreasing last positions of the distinct symbols of s
    private final int[] symbolSupport; // number of sequences containing each symbol

    /**
     * Creates a sequence database without timestamps.
     *
     * @param name      the name of the database
     * @param sequences the sequences, each being an array of positive symbol ids
     */
    public SequenceDb(String name, int[][] sequences) {
        this(name, sequences, null, false);
    }

    /**
     * Creates a sequence database with one timestamp per event.
     *
     * @param name      the name of the database
     * @param sequences the sequences, each being an array of positive symbol ids
     * @param times     the timestamps, {@code times[s][j]} being the time of event j
     *                  of sequence s; must have the same shape as {@code sequences}
     *                  and be non-decreasing within each sequence
     */
    public SequenceDb(String name, int[][] sequences, int[][] times) {
        this(name, sequences, times, true);
    }

    private SequenceDb(String name, int[][] sequences, int[][] times, boolean timed) {
        if (timed) {
            if (times == null)
                throw new IllegalArgumentException("times must not be null");
            if (times.length != sequences.length)
                throw new IllegalArgumentException("times has " + times.length
                        + " sequences but the database has " + sequences.length);
            for (int s = 0; s < sequences.length; s++) {
                if (times[s].length != sequences[s].length)
                    throw new IllegalArgumentException("sequence " + s + " has " + sequences[s].length
                            + " events but " + times[s].length + " timestamps");
                for (int j = 1; j < times[s].length; j++)
                    if (times[s][j] < times[s][j - 1])
                        throw new IllegalArgumentException("timestamps of sequence " + s
                                + " are not non-decreasing at position " + j);
            }
        }
        this.times = timed ? times : null;
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
     * Reads a timed sequence database in the SPMF-with-time format (the format
     * of the Hirate-Yamana benchmarks and of OscarML's SpmfWithTimeFormat).
     * Each non-blank line is one sequence of {@code <t> sym} events:
     * a {@code <t>} token gives the timestamp of the following symbols,
     * -1 terminates an itemset (itemsets are assumed to be singletons, so -1
     * tokens are skipped) and -2 terminates the sequence.
     * Example line: {@code <0> 1 -1 <2> 4 -1 -2}.
     *
     * @param path the path to the file
     * @return the timed sequence database, named after the file base name
     */
    public static SequenceDb readSpmfWithTime(String path) {
        List<int[]> sequences = new ArrayList<>();
        List<int[]> times = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path));
        } catch (IOException e) {
            throw new RuntimeException("cannot read " + path, e);
        }
        List<Integer> symbols = new ArrayList<>();
        List<Integer> stamps = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank())
                continue;
            symbols.clear();
            stamps.clear();
            int time = 0;
            boolean timeSeen = false;
            for (String token : line.trim().split("\\s+")) {
                if (token.startsWith("<") && token.endsWith(">")) {
                    time = Integer.parseInt(token.substring(1, token.length() - 1));
                    timeSeen = true;
                    continue;
                }
                int v = Integer.parseInt(token);
                if (v == -2)
                    break;
                if (v == -1)
                    continue;
                if (v <= 0)
                    throw new IllegalArgumentException("invalid symbol " + v + " in " + path);
                if (!timeSeen)
                    throw new IllegalArgumentException("symbol " + v + " without a <time> prefix in " + path);
                symbols.add(v);
                stamps.add(time);
            }
            sequences.add(symbols.stream().mapToInt(Integer::intValue).toArray());
            times.add(stamps.stream().mapToInt(Integer::intValue).toArray());
        }
        String fileName = Path.of(path).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String name = dot > 0 ? fileName.substring(0, dot) : fileName;
        return new SequenceDb(name, sequences.toArray(new int[0][]), times.toArray(new int[0][]));
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
     * Returns whether the database carries one timestamp per event.
     *
     * @return true if the database is timed
     */
    public boolean hasTimes() {
        return times != null;
    }

    /**
     * Returns the timestamp of an event. The database must be timed.
     *
     * @param s the index of the sequence in {@code 0..nSeqs()-1}
     * @param j the 0-based position of the event in sequence s
     * @return the timestamp of the event in position j of sequence s
     */
    public int time(int s, int j) {
        if (times == null)
            throw new IllegalStateException("database " + name + " has no timestamps");
        return times[s][j];
    }

    /**
     * Returns the timestamps of a sequence. The database must be timed.
     * The returned array is the internal one and must not be modified.
     *
     * @param s the index of the sequence in {@code 0..nSeqs()-1}
     * @return the timestamps of the events of sequence s
     */
    public int[] times(int s) {
        if (times == null)
            throw new IllegalStateException("database " + name + " has no timestamps");
        return times[s];
    }

    /**
     * Computes the earliest positions compatible with a minimum gap, as
     * PPICt's nextPosGap precomputation (DatasetUtils.getSDBNextPosGap):
     * for each sequence s and each 0-based position j, entry {@code [s][j]} is
     * the smallest position {@code u >= j} with {@code time(s,u) >= time(s,j) + mingap},
     * or the length of sequence s if there is none.
     * The database must be timed.
     *
     * @param mingap the minimum gap (callers enforcing the PPICt convention
     *               should pass {@link TimeOption#effectiveMingap()})
     * @return the next-position-per-gap table, one array per sequence
     */
    public int[][] nextPosGap(int mingap) {
        if (times == null)
            throw new IllegalStateException("database " + name + " has no timestamps");
        int[][] nextPos = new int[sequences.length][];
        for (int s = 0; s < sequences.length; s++) {
            int[] t = times[s];
            int len = t.length;
            nextPos[s] = new int[len];
            int u = 0;
            for (int j = 0; j < len; j++) {
                if (u < j)
                    u = j;
                while (u < len && t[u] < (long) t[j] + mingap)
                    u++;
                nextPos[s][j] = u;
            }
        }
        return nextPos;
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
