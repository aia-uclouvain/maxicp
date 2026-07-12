/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPSeqPatternVar;
import org.maxicp.cp.engine.core.CPSeqPatternVarImpl;
import org.maxicp.cp.engine.core.CPSeqPatternVarTimedImpl;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.Dfa;
import org.maxicp.cp.engine.core.mining.SeqMiningTestUtils;
import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.cp.engine.core.mining.TimeOption;
import org.maxicp.search.DFSearch;
import org.maxicp.search.PatternSearches;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class MinSupportSeqTimedTest extends CPSolverTest {

    /**
     * Collects all the solutions of the append/exclude search as a map from the
     * space-joined pattern (including "" for the empty pattern) to its complete
     * support (= support for untimed variables and minspan 0), checking that no
     * pattern is enumerated twice.
     */
    private static Map<String, Integer> collectSolutions(CPSolver cp, CPSeqPatternVar p) {
        DFSearch dfs = CPFactory.makeDfs(cp, PatternSearches.appendExclude(p));
        Map<String, Integer> sols = new HashMap<>();
        int[] buf = new int[p.database().maxSeqLength()];
        dfs.onSolution(() -> {
            assertTrue(p.isFixed());
            int len = p.fillPrefix(buf);
            String key = SeqMiningTestUtils.join(buf, len);
            assertNull(sols.put(key, p.completeSupport()), "pattern enumerated twice: " + key);
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(sols.size(), stats.numberOfSolutions());
        return sols;
    }

    /**
     * Reference test of the PPICt paper implementation: the Hirate-Yamana
     * benchmark of OscarML's PPICtTest ("SPM with time check solution"), i.e.
     * input test.postime.spmf (timestamps = positions 0,1,2,...), relative
     * support 50% of 4 sequences = 2, and TimeOption(minspan=0, maxspan=2,
     * mingap=0 (normalized to 1), maxgap=2), against the spmf HirateYamana
     * output test_HirateYamana_2_02_02.txt.
     */
    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testHirateYamanaReference(Supplier<CPSolver> solverSupplier) {
        SequenceDb db = SequenceDb.readSpmfWithTime("data/MINING/SEQUENCE/test.postime.spmf");
        assertEquals(4, db.nSeqs());
        TimeOption time = new TimeOption(0, 2, 0, 2);
        int theta = 2; // 50% of 4 sequences
        Map<String, Integer> oracle =
                SeqMiningTestUtils.readSols("data/MINING/SEQUENCE/oracle/test_HirateYamana_2_02_02.txt");

        CPSolver cp = solverSupplier.get();
        CPSeqPatternVar p = CPFactory.makeSeqPatternVar(cp, db, time);
        cp.post(new MinSupportSeq(p, theta));
        Map<String, Integer> sols = collectSolutions(cp, p);
        // the empty pattern is always frequent but absent from the reference file
        assertEquals(db.nSeqs(), sols.remove(""));

        // same patterns, and the spmf supports never exceed the sequence supports
        // (the reference OscarML test asserts exactly this: set equality and <=)
        assertEquals(oracle.keySet(), sols.keySet());
        oracle.forEach((pattern, support) ->
                assertTrue(support <= sols.get(pattern), pattern + ": " + support + " > " + sols.get(pattern)));

        // exact supports: spmf's HirateYamana outputs interval-annotated patterns,
        // so "3 2" appears twice in the file with support 2 each (once per time
        // interval); its sequence support is 3 (s0: 3@3->2@4, s1: 3@4->2@6,
        // s3: 3@1->2@3 or 3@2->2@3). All the other lines are plain sequence supports.
        Map<String, Integer> expected = new HashMap<>(oracle);
        expected.put("3 2", 3);
        assertEquals(expected, sols);

        // cross-validate every reported support with the brute-force timed oracle
        sols.forEach((pattern, support) -> {
            int[] word = Arrays.stream(pattern.split(" ")).mapToInt(Integer::parseInt).toArray();
            assertEquals(SeqMiningTestUtils.timedSupport(db, word, word.length, time), support, pattern);
        });
    }

    /**
     * With unconstrained time restrictions (and events at distinct times),
     * the timed variable must behave exactly as the untimed PPIC variable.
     */
    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testUnconstrainedEquivalence(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 10; seed++) {
            Random rand = new Random(seed);
            int nSeqs = 1 + rand.nextInt(6);
            int nSymbols = 2 + rand.nextInt(4);
            SequenceDb db = SeqMiningTestUtils.randomDb(rand, nSeqs, nSymbols, 5);
            // same sequences with consecutive timestamps 1, 2, 3, ...
            int[][] sequences = new int[nSeqs][];
            int[][] times = new int[nSeqs][];
            for (int s = 0; s < nSeqs; s++) {
                sequences[s] = db.sequence(s);
                times[s] = new int[sequences[s].length];
                for (int i = 0; i < times[s].length; i++)
                    times[s][i] = i + 1;
            }
            SequenceDb timedDb = new SequenceDb("timed", sequences, times);
            int theta = 1 + rand.nextInt(2);

            Map<String, Integer> untimedSols;
            CPSolver cp = solverSupplier.get();
            CPSeqPatternVar untimed = new CPSeqPatternVarImpl(cp, db);
            try {
                cp.post(new MinSupportSeq(untimed, theta));
                untimedSols = collectSolutions(cp, untimed);
            } catch (InconsistencyException e) {
                untimedSols = null; // even the empty pattern is infrequent
            }

            CPSolver cpTimed = solverSupplier.get();
            try {
                CPSeqPatternVar timed = new CPSeqPatternVarTimedImpl(cpTimed, timedDb, TimeOption.unconstrained());
                cpTimed.post(new MinSupportSeq(timed, theta));
                assertEquals(untimedSols, collectSolutions(cpTimed, timed), "seed=" + seed);
            } catch (InconsistencyException e) {
                assertNull(untimedSols, "seed=" + seed);
            }
        }
    }

    /**
     * Differential test against the brute-force timed enumeration on random
     * timed databases and random time restrictions, including minspan > 0
     * (where the complete support at the leaves differs from the anti-monotone
     * support used for pruning).
     */
    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testAgainstBruteForce(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 10; seed++) {
            Random rand = new Random(seed);
            int nSeqs = 1 + rand.nextInt(6);
            int nSymbols = 2 + rand.nextInt(4);
            SequenceDb db = SeqMiningTestUtils.randomTimedDb(rand, nSeqs, nSymbols, 5, 3);
            int minspan = rand.nextInt(4); // often > 0
            int mingap = rand.nextInt(3);
            TimeOption time = new TimeOption(minspan, minspan + rand.nextInt(9), mingap, mingap + rand.nextInt(6));
            int theta = 1 + rand.nextInt(2);
            String ctx = "seed=" + seed + " theta=" + theta + " " + time;
            Map<String, Integer> expected =
                    SeqMiningTestUtils.timedFrequentSequences(db, theta, db.maxSeqLength(), time);
            CPSolver cp = solverSupplier.get();
            try {
                CPSeqPatternVar p = new CPSeqPatternVarTimedImpl(cp, db, time);
                cp.post(new MinSupportSeq(p, theta));
                assertEquals(expected, collectSolutions(cp, p), ctx);
            } catch (InconsistencyException e) {
                // no pattern (not even the empty one) satisfies the query
                assertTrue(expected.isEmpty(), ctx);
            }
        }
    }

    /**
     * Composition: the untouched MinSupportSeq + PatternLengthSeq + PrefixAutomaton
     * constraints filter the shared frontier of one time-constrained variable,
     * compared with the triple-filtered brute-force enumeration.
     */
    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testCompositionWithLengthAndAutomaton(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 8; seed++) {
            Random rand = new Random(seed + 700);
            int nSeqs = 2 + rand.nextInt(5);
            int nSymbols = 3 + rand.nextInt(3);
            SequenceDb db = SeqMiningTestUtils.randomTimedDb(rand, nSeqs, nSymbols, 5, 3);
            if (db.nSymbols() < 2)
                continue; // no symbol occurs in the random database
            int minspan = rand.nextInt(3); // often > 0
            int mingap = rand.nextInt(2);
            TimeOption time = new TimeOption(minspan, minspan + rand.nextInt(9), mingap, mingap + rand.nextInt(6));
            int theta = 1 + rand.nextInt(2);
            int symbol = 1 + rand.nextInt(db.nSymbols() - 1);
            int minLength = 1 + rand.nextInt(2);
            int maxLength = minLength + 1 + rand.nextInt(2);
            Dfa[] automata = new Dfa[]{
                    PrefixAutomatonTest.startsWith(db.nSymbols(), symbol),
                    PrefixAutomatonTest.contains(db.nSymbols(), symbol),
                    PrefixAutomatonTest.endsWith(db.nSymbols(), symbol),
                    PrefixAutomatonTest.evenLength(db.nSymbols()),
                    PrefixAutomatonTest.noImmediateRepeat(db.nSymbols())};

            for (Dfa dfa : automata) {
                Map<String, Integer> expected = new HashMap<>();
                SeqMiningTestUtils.timedFrequentSequences(db, theta, db.maxSeqLength(), time)
                        .forEach((pattern, support) -> {
                            int[] word = pattern.isEmpty() ? new int[0]
                                    : Arrays.stream(pattern.split(" ")).mapToInt(Integer::parseInt).toArray();
                            if (word.length >= minLength && word.length <= maxLength
                                    && dfa.accepts(word, word.length))
                                expected.put(pattern, support);
                        });

                String ctx = "seed=" + seed + " theta=" + theta + " " + time
                        + " symbol=" + symbol + " window=[" + minLength + "," + maxLength + "]";
                CPSolver cp = solverSupplier.get();
                try {
                    CPSeqPatternVar p = CPFactory.makeSeqPatternVar(cp, db, time);
                    cp.post(new MinSupportSeq(p, theta));
                    cp.post(new PrefixAutomaton(p, dfa));
                    cp.post(new PatternLengthSeq(p, minLength, maxLength));
                    assertEquals(expected, collectSolutions(cp, p), ctx);
                } catch (InconsistencyException e) {
                    assertTrue(expected.isEmpty(), ctx);
                }
            }
        }
    }

    /**
     * The updated termination rule keeps the untimed behavior unchanged
     * (completeSupport() == support() there) and, on a timed variable with
     * minspan > 0, forbids terminating on a prefix whose complete support is
     * below the threshold while still allowing frequent strict extensions.
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMinspanForbidsShortTermination(CPSolver cp) {
        // two identical sequences 1@0, 2@1, 3@5: with minspan 5 the patterns
        // occurring with span >= 5 are <1 3> and <1 2 3> only; every strict
        // prefix of <1 2 3> has spans below 5 and must not terminate
        SequenceDb db = new SequenceDb("toy",
                new int[][]{{1, 2, 3}, {1, 2, 3}},
                new int[][]{{0, 1, 5}, {0, 1, 5}});
        TimeOption time = new TimeOption(5, 10, 1, 10);
        CPSeqPatternVar p = new CPSeqPatternVarTimedImpl(cp, db, time);
        cp.post(new MinSupportSeq(p, 2));
        assertFalse(p.canTerminate()); // the empty pattern has span 0 < 5
        p.append(1);
        cp.fixPoint();
        assertFalse(p.canTerminate()); // span 0 < 5
        p.append(2);
        cp.fixPoint();
        assertFalse(p.canTerminate()); // best span of <1 2> is 1 < 5
        p.append(3);
        cp.fixPoint();
        assertTrue(p.isTerminated()); // frontier empty and complete support 2 >= theta
        assertEquals(2, p.completeSupport());
    }
}
