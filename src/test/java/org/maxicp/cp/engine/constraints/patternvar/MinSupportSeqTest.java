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
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.SeqMiningTestUtils;
import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.PatternSearches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class MinSupportSeqTest extends CPSolverTest {

    /**
     * Collects all the solutions of the append/exclude search as a map from the
     * space-joined pattern (including "" for the empty pattern) to its support,
     * checking that no pattern is enumerated twice.
     */
    private static Map<String, Integer> collectSolutions(CPSolver cp, CPSeqPatternVar p) {
        DFSearch dfs = CPFactory.makeDfs(cp, PatternSearches.appendExclude(p));
        Map<String, Integer> sols = new HashMap<>();
        int[] buf = new int[p.database().maxSeqLength()];
        dfs.onSolution(() -> {
            assertTrue(p.isFixed());
            int len = p.fillPrefix(buf);
            String key = SeqMiningTestUtils.join(buf, len);
            assertNull(sols.put(key, p.support()), "pattern enumerated twice: " + key);
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(sols.size(), stats.numberOfSolutions());
        return sols;
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testReferenceOutput(Supplier<CPSolver> solverSupplier) {
        SequenceDb db = SequenceDb.readSpmf("data/MINING/SEQUENCE/test.spmf");
        for (int theta : new int[]{2, 3}) {
            Map<String, Integer> expected =
                    SeqMiningTestUtils.readSols("data/MINING/SEQUENCE/oracle/test_prefixspan_" + theta + ".txt");
            CPSolver cp = solverSupplier.get();
            CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, db);
            cp.post(new MinSupportSeq(p, theta));
            Map<String, Integer> sols = collectSolutions(cp, p);
            // the empty pattern is always frequent but absent from the reference file
            assertEquals(db.nSeqs(), sols.remove(""));
            assertEquals(expected, sols, "theta=" + theta);
        }
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testAgainstBruteForce(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 10; seed++) {
            Random rand = new Random(seed);
            int nSeqs = 1 + rand.nextInt(6);
            int nSymbols = 2 + rand.nextInt(4);
            SequenceDb db = SeqMiningTestUtils.randomDb(rand, nSeqs, nSymbols, 4);
            int theta = 1 + rand.nextInt(2);
            Map<String, Integer> expected = SeqMiningTestUtils.frequentSequences(db, theta, db.maxSeqLength());
            CPSolver cp = solverSupplier.get();
            CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, db);
            try {
                cp.post(new MinSupportSeq(p, theta));
            } catch (InconsistencyException e) {
                // only possible when even the empty pattern is infrequent
                assertTrue(nSeqs < theta, "seed=" + seed);
                assertTrue(expected.isEmpty(), "seed=" + seed);
                continue;
            }
            assertEquals(expected, collectSolutions(cp, p), "seed=" + seed);
        }
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testWithPatternLength(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 10; seed++) {
            Random rand = new Random(seed + 1000);
            int nSeqs = 1 + rand.nextInt(6);
            int nSymbols = 2 + rand.nextInt(4);
            SequenceDb db = SeqMiningTestUtils.randomDb(rand, nSeqs, nSymbols, 4);
            int theta = 1 + rand.nextInt(2);
            int minLength = 1 + rand.nextInt(2);
            int maxLength = minLength + rand.nextInt(2);
            Map<String, Integer> expected = new HashMap<>();
            SeqMiningTestUtils.frequentSequences(db, theta, db.maxSeqLength()).forEach((pattern, support) -> {
                int len = pattern.isEmpty() ? 0 : pattern.split(" ").length;
                if (len >= minLength && len <= maxLength)
                    expected.put(pattern, support);
            });
            CPSolver cp = solverSupplier.get();
            CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, db);
            String ctx = "seed=" + seed + " theta=" + theta + " window=[" + minLength + "," + maxLength + "]";
            try {
                cp.post(new MinSupportSeq(p, theta));
                cp.post(new PatternLengthSeq(p, minLength, maxLength));
            } catch (InconsistencyException e) {
                assertTrue(expected.isEmpty(), ctx);
                continue;
            }
            Map<String, Integer> sols = collectSolutions(cp, p);
            assertEquals(expected, sols, ctx);
            sols.forEach((pattern, support) -> {
                int len = pattern.isEmpty() ? 0 : pattern.split(" ").length;
                assertTrue(len >= minLength && len <= maxLength, ctx + " length of " + pattern);
            });
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInvalidTheta(CPSolver cp) {
        SequenceDb db = new SequenceDb("toy", new int[][]{{1, 2}});
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, db);
        assertThrows(IllegalArgumentException.class, () -> new MinSupportSeq(p, 0));
        assertThrows(IllegalArgumentException.class, () -> new PatternLengthSeq(p, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new PatternLengthSeq(p, -1, 1));
    }
}
