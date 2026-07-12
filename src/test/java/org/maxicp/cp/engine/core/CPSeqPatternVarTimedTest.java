/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.mining.SeqMiningTestUtils;
import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.cp.engine.core.mining.TimeOption;
import org.maxicp.state.StateManager;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class CPSeqPatternVarTimedTest extends CPSolverTest {

    /**
     * The timed db of the PPICt paper (= data/MINING/SEQUENCE/test.time.spmf),
     * with a=1, b=2, c=3, d=4, e=5:
     * s0 = (a,2)(b,5)(d,6)(c,10)(b,11)
     * s1 = (b,1)(a,2)(a,9)(d,12)(c,15)(a,18)(b,24)
     * s2 = (a,2)(b,4)(d,6)(d,8)(b,10)(e,12)(c,14)
     * s3 = (a,1)(c,2)(c,3)(b,4)
     */
    private static SequenceDb paperDb() {
        return new SequenceDb("ppict", new int[][]{
                {1, 2, 4, 3, 2},
                {2, 1, 1, 4, 3, 1, 2},
                {1, 2, 4, 4, 2, 5, 3},
                {1, 3, 3, 2}},
                new int[][]{
                        {2, 5, 6, 10, 11},
                        {1, 2, 9, 12, 15, 18, 24},
                        {2, 4, 6, 8, 10, 12, 14},
                        {1, 2, 3, 4}});
    }

    // the TimeOption of the PPICt paper example
    private static final TimeOption PAPER_TIME = new TimeOption(0, 10, 3, 7);

    @Test
    public void testTimeOptionValidation() {
        assertThrows(IllegalArgumentException.class, () -> new TimeOption(-1, 2, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new TimeOption(3, 2, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new TimeOption(0, 2, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new TimeOption(0, 2, 3, 2));
        assertEquals(1, new TimeOption(0, 2, 0, 2).effectiveMingap());
        assertEquals(2, new TimeOption(0, 9, 2, 4).effectiveMingap());
        assertEquals(0, TimeOption.unconstrained().minspan());
        assertEquals(Integer.MAX_VALUE, TimeOption.unconstrained().maxgap());
    }

    @Test
    public void testReadSpmfWithTime() {
        SequenceDb db = SequenceDb.readSpmfWithTime("data/MINING/SEQUENCE/test.time.spmf");
        SequenceDb expected = paperDb();
        assertTrue(db.hasTimes());
        assertEquals(expected.nSeqs(), db.nSeqs());
        assertEquals(expected.nSymbols(), db.nSymbols());
        for (int s = 0; s < expected.nSeqs(); s++) {
            assertArrayEquals(expected.sequence(s), db.sequence(s), "sequence " + s);
            assertArrayEquals(expected.times(s), db.times(s), "times " + s);
        }
        SequenceDb untimed = SequenceDb.readSpmf("data/MINING/SEQUENCE/test.spmf");
        assertFalse(untimed.hasTimes());
        assertThrows(IllegalStateException.class, () -> untimed.time(0, 0));
        assertThrows(IllegalStateException.class, () -> untimed.nextPosGap(1));
    }

    @Test
    public void testTimedDbValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new SequenceDb("bad", new int[][]{{1, 2}}, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SequenceDb("bad", new int[][]{{1, 2}}, new int[][]{{1, 2}, {3}}));
        assertThrows(IllegalArgumentException.class,
                () -> new SequenceDb("bad", new int[][]{{1, 2}}, new int[][]{{1}}));
        assertThrows(IllegalArgumentException.class,
                () -> new SequenceDb("bad", new int[][]{{1, 2}}, new int[][]{{2, 1}})); // decreasing
        // equal consecutive timestamps are allowed
        SequenceDb db = new SequenceDb("ties", new int[][]{{1, 2}}, new int[][]{{3, 3}});
        assertEquals(3, db.time(0, 1));
    }

    @Test
    public void testNextPosGap() {
        SequenceDb db = paperDb();
        // s1 times = 1 2 9 12 15 18 24, mingap 3:
        // from t=1 -> first t>=4 is 9 (pos 2), from t=2 -> 9 (pos 2), from t=9 -> 12 (pos 3),
        // from t=12 -> 15 (pos 4), from t=15 -> 18 (pos 5), from t=18 -> 24 (pos 6), from t=24 -> none (7)
        assertArrayEquals(new int[]{2, 2, 3, 4, 5, 6, 7}, db.nextPosGap(3)[1]);
        // s3 times = 1 2 3 4, mingap 3: 3, 5(=4? t>=5 none -> len), ...
        assertArrayEquals(new int[]{3, 4, 4, 4}, db.nextPosGap(3)[3]);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRequiresTimedDb(CPSolver cp) {
        SequenceDb untimed = new SequenceDb("toy", new int[][]{{1, 2}});
        assertThrows(IllegalArgumentException.class,
                () -> new CPSeqPatternVarTimedImpl(cp, untimed, TimeOption.unconstrained()));
        assertThrows(IllegalArgumentException.class,
                () -> org.maxicp.cp.CPFactory.makeSeqPatternVar(cp, untimed, TimeOption.unconstrained()));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInitialState(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarTimedImpl(cp, paperDb(), PAPER_TIME);
        assertEquals(0, p.prefixLength());
        assertEquals(4, p.support());
        assertEquals(4, p.completeSupport()); // minspan = 0
        assertEquals(5, p.nCandidates());
        // a single event always satisfies the anti-monotone policy
        assertEquals(4, p.supportAfter(1));
        assertEquals(4, p.supportAfter(2));
        assertEquals(4, p.supportAfter(3));
        assertEquals(3, p.supportAfter(4));
        assertEquals(1, p.supportAfter(5));
        assertTrue(p.canTerminate());
        assertFalse(p.isTerminated());
        assertFalse(p.isFixed());
        assertEquals(6, p.nSymbols());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAppendEmbeddingWindows(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarTimedImpl(cp, paperDb(), PAPER_TIME);
        p.append(1);
        assertEquals(1, p.prefixLength());
        assertEquals(4, p.support());
        // extension windows (gap in [3,7], span <= 10, so window [t+3, t+7] of each embedding):
        // s0 from (a,2): [5,9] -> {b@5, d@6}
        // s1 from (a,2): [5,9] -> {a@9}; from (a,9): [12,16] -> {d@12, c@15}; from (a,18): [21,25] -> {b@24}
        // s2 from (a,2): [5,9] -> {d@6, d@8}
        // s3 from (a,1): [4,8] -> {b@4}
        assertEquals(1, p.supportAfter(1)); // s1
        assertEquals(3, p.supportAfter(2)); // s0, s1, s3
        assertEquals(1, p.supportAfter(3)); // s1 (c@10 of s0 at gap 8 > maxgap, c@14 of s2 at gap 12)
        assertEquals(3, p.supportAfter(4)); // s0, s1, s2
        assertEquals(0, p.supportAfter(5)); // e@12 of s2 at gap 10 > maxgap
        assertEquals(4, p.nCandidates());
        assertFalse(p.isCandidate(5));

        p.append(2);
        assertEquals(2, p.prefixLength());
        // embeddings of <a b>: s0 (2,5); s1 (18,24); s3 (1,4); none in s2
        assertEquals(3, p.support());
        // extension windows: s0 from (2,5): [8, min(5+7, 2+10)=12] -> {c@10, b@11}
        // s1 from (18,24): [27, 28] -> {}; s3 from (1,4): [7,8] -> {}
        assertEquals(0, p.supportAfter(1));
        assertEquals(1, p.supportAfter(2));
        assertEquals(1, p.supportAfter(3));
        assertEquals(0, p.supportAfter(4));
        assertEquals(0, p.supportAfter(5));
        assertEquals(2, p.nCandidates());
        assertTrue(p.isCandidate(2) && p.isCandidate(3));
        assertEquals("⟨1 2⟩ +2 candidates, support=3", p.toString());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testGapExclusions(CPSolver cp) {
        // one sequence 1@0, 2@3, 3@4
        SequenceDb db = new SequenceDb("gap", new int[][]{{1, 2, 3}}, new int[][]{{0, 3, 4}});
        CPSeqPatternVar p = new CPSeqPatternVarTimedImpl(cp, db, new TimeOption(0, 100, 3, 3));
        p.append(1);
        assertEquals(1, p.supportAfter(2)); // gap 3 in [3,3]
        assertEquals(0, p.supportAfter(3)); // gap 4 > maxgap 3

        CPSeqPatternVar q = new CPSeqPatternVarTimedImpl(cp, db, new TimeOption(0, 100, 4, 7));
        q.append(1);
        assertEquals(0, q.supportAfter(2)); // gap 3 < mingap 4
        assertEquals(1, q.supportAfter(3)); // gap 4 in [4,7]
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMaxSpanExclusion(CPSolver cp) {
        // one sequence 1@0, 2@2, 3@4: all gaps fit but the total span 4 exceeds maxspan 3
        SequenceDb db = new SequenceDb("span", new int[][]{{1, 2, 3}}, new int[][]{{0, 2, 4}});
        CPSeqPatternVar p = new CPSeqPatternVarTimedImpl(cp, db, new TimeOption(0, 3, 1, 7));
        p.append(1);
        assertEquals(1, p.supportAfter(2)); // <1 2> has span 2 <= 3
        assertEquals(0, p.supportAfter(3)); // 1@0 -> 3@4: gap 4 <= maxgap 7 but span 4 > maxspan 3
        p.append(2);
        // extending <1 2> (first at time 0) with 3@4 would reach span 4 > maxspan: frontier empty
        assertTrue(p.isTerminated()); // auto-terminated on the empty frontier (minspan 0)
        assertEquals(1, p.support());
        assertEquals(1, p.completeSupport());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMingapZeroIsNormalizedToOne(CPSolver cp) {
        // simultaneous events can not be matched consecutively: mingap 0 means 1 (PPICt convention)
        SequenceDb db = new SequenceDb("ties", new int[][]{{1, 2, 3}}, new int[][]{{5, 5, 6}});
        CPSeqPatternVar p = new CPSeqPatternVarTimedImpl(cp, db, new TimeOption(0, 10, 0, 10));
        p.append(1);
        assertEquals(0, p.supportAfter(2)); // 2@5 is simultaneous with 1@5
        assertEquals(1, p.supportAfter(3)); // 3@6 at gap 1
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCompleteSupportMinspanDivergence(CPSolver cp) {
        // minspan is not anti-monotone: it must not affect support()/supportAfter()
        CPSeqPatternVar p = new CPSeqPatternVarTimedImpl(cp, paperDb(), new TimeOption(4, 10, 3, 7));
        assertEquals(4, p.support());
        assertEquals(0, p.completeSupport()); // the empty pattern has span 0 < 4
        p.append(1);
        assertEquals(4, p.support());
        assertEquals(0, p.completeSupport()); // single-event embeddings have span 0 < 4
        assertEquals(3, p.supportAfter(2)); // same as with minspan = 0
        p.append(2);
        // embeddings of <a b>: s0 (2,5) span 3; s1 (18,24) span 6; s3 (1,4) span 3
        assertEquals(3, p.support());
        assertEquals(1, p.completeSupport()); // only s1 reaches span >= 4
        // with minspan = 3 all three occurrences qualify
        CPSeqPatternVar q = new CPSeqPatternVarTimedImpl(cp, paperDb(), new TimeOption(3, 10, 3, 7));
        q.append(1);
        q.append(2);
        assertEquals(3, q.support());
        assertEquals(3, q.completeSupport());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTerminateRefusedWhenCompleteSupportZero(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarTimedImpl(cp, paperDb(), new TimeOption(7, 10, 3, 7));
        p.append(1);
        assertEquals(0, p.completeSupport());
        assertThrows(InconsistencyException.class, p::terminate); // span 0 < minspan 7
        assertFalse(p.isTerminated()); // the failed terminate did not commit
        p.append(2);
        // best span of <a b> is 6 (s1): still below minspan 7
        assertEquals(3, p.support());
        assertEquals(0, p.completeSupport());
        assertThrows(InconsistencyException.class, p::terminate);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAutoTerminateFailsWhenCompleteSupportZero(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarTimedImpl(cp, paperDb(), new TimeOption(7, 10, 3, 7));
        p.append(1);
        p.append(2);
        assertEquals(2, p.nCandidates()); // {2, 3}
        p.excludeNext(2);
        // emptying the frontier auto-terminates, but <a b> has no occurrence of span >= 7
        assertThrows(InconsistencyException.class, () -> p.excludeNext(3));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTerminateAllowedWhenMinspanReached(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarTimedImpl(cp, paperDb(), new TimeOption(6, 10, 3, 7));
        p.append(1);
        p.append(2);
        assertEquals(1, p.completeSupport()); // s1 at span exactly 6 (inclusive bound)
        p.terminate();
        assertTrue(p.isFixed());
        assertEquals(1, p.completeSupport());
        assertEquals(3, p.support());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testForbidTerminateResetByAppend(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarTimedImpl(cp, paperDb(), PAPER_TIME);
        p.forbidTerminate();
        assertFalse(p.canTerminate());
        assertThrows(InconsistencyException.class, p::terminate);
        p.append(2);
        assertTrue(p.canTerminate());
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testSaveRestoreRandomWalk(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 8; seed++) {
            Random rand = new Random(seed);
            SequenceDb db = SeqMiningTestUtils.randomTimedDb(
                    rand, 1 + rand.nextInt(6), 2 + rand.nextInt(4), 5, 3);
            TimeOption time = randomTimeOption(rand, seed);
            CPSolver cp = solverSupplier.get();
            CPSeqPatternVar p;
            try {
                p = new CPSeqPatternVarTimedImpl(cp, db, time);
            } catch (InconsistencyException e) {
                // no valid pattern at all: even the empty pattern has no valid occurrence
                assertEquals(0, SeqMiningTestUtils.timedSupport(db, new int[0], 0, time), "seed=" + seed);
                continue;
            }
            StateManager sm = cp.getStateManager();
            Deque<Snapshot> stack = new ArrayDeque<>();
            int[] candidates = new int[p.nSymbols()];
            int[] pattern = new int[db.maxSeqLength() + 1];
            for (int step = 0; step < 200; step++) {
                checkKernel(p, db, time);
                double r = rand.nextDouble();
                if (!stack.isEmpty() && (r < 0.3 || p.isTerminated())) {
                    sm.restoreState();
                    assertSnapshot(stack.pop(), p);
                } else if (!p.isTerminated()) {
                    stack.push(snapshot(p));
                    sm.saveState();
                    int n = p.fillCandidates(candidates);
                    int a = candidates[rand.nextInt(n)];
                    try {
                        if (r < 0.65)
                            p.append(a);
                        else
                            p.excludeNext(a);
                    } catch (InconsistencyException e) {
                        // an auto-termination found no valid complete occurrence:
                        // the (extended) prefix must indeed have complete support 0
                        int len = p.fillPrefix(pattern);
                        assertEquals(0, SeqMiningTestUtils.timedSupport(db, pattern, len, time),
                                "seed=" + seed + " after failed op on " + a);
                        sm.restoreState();
                        assertSnapshot(stack.pop(), p);
                    }
                } else {
                    break; // terminated at the root
                }
            }
            while (!stack.isEmpty()) {
                sm.restoreState();
                assertSnapshot(stack.pop(), p);
                checkKernel(p, db, time);
            }
        }
    }

    static TimeOption randomTimeOption(Random rand, int seed) {
        if (seed % 4 == 0)
            return TimeOption.unconstrained();
        int minspan = rand.nextInt(4); // often > 0
        int mingap = rand.nextInt(3);
        return new TimeOption(minspan, minspan + rand.nextInt(9), mingap, mingap + rand.nextInt(6));
    }

    private record Snapshot(int[] prefix, int support, int completeSupport, int[] supportAfter,
                            Set<Integer> candidates, boolean terminated, boolean canTerminate) {
    }

    private static Snapshot snapshot(CPSeqPatternVar p) {
        int[] prefix = new int[p.prefixLength()];
        p.fillPrefix(prefix);
        int[] supportAfter = new int[p.nSymbols()];
        for (int a = 1; a < p.nSymbols(); a++)
            supportAfter[a] = p.supportAfter(a);
        int[] buf = new int[Math.max(p.nSymbols() - 1, 1)];
        int n = p.fillCandidates(buf);
        Set<Integer> candidates = new HashSet<>();
        for (int i = 0; i < n; i++)
            candidates.add(buf[i]);
        return new Snapshot(prefix, p.support(), p.completeSupport(), supportAfter, candidates,
                p.isTerminated(), p.canTerminate());
    }

    private static void assertSnapshot(Snapshot s, CPSeqPatternVar p) {
        int[] prefix = new int[p.prefixLength()];
        p.fillPrefix(prefix);
        assertArrayEquals(s.prefix(), prefix);
        assertEquals(s.support(), p.support());
        assertEquals(s.completeSupport(), p.completeSupport());
        for (int a = 1; a < p.nSymbols(); a++)
            assertEquals(s.supportAfter()[a], p.supportAfter(a), "supportAfter(" + a + ")");
        int[] buf = new int[Math.max(p.nSymbols() - 1, 1)];
        int n = p.fillCandidates(buf);
        Set<Integer> candidates = new HashSet<>();
        for (int i = 0; i < n; i++)
            candidates.add(buf[i]);
        assertEquals(s.candidates(), candidates);
        assertEquals(s.terminated(), p.isTerminated());
        assertEquals(s.canTerminate(), p.canTerminate());
    }

    /**
     * Checks the PPICt kernel against the brute-force timed-embedding oracle:
     * the anti-monotone supports of the prefix and of all its one-symbol
     * extensions, and the complete (minspan-aware) support of the prefix.
     */
    private static void checkKernel(CPSeqPatternVar p, SequenceDb db, TimeOption time) {
        int[] pattern = new int[db.maxSeqLength() + 1];
        int len = p.fillPrefix(pattern);
        assertEquals(SeqMiningTestUtils.timedPrefixSupport(db, pattern, len, time), p.support(),
                "support of " + SeqMiningTestUtils.join(pattern, len));
        assertEquals(SeqMiningTestUtils.timedSupport(db, pattern, len, time), p.completeSupport(),
                "completeSupport of " + SeqMiningTestUtils.join(pattern, len));
        for (int a = 1; a < db.nSymbols(); a++) {
            pattern[len] = a;
            assertEquals(SeqMiningTestUtils.timedPrefixSupport(db, pattern, len + 1, time), p.supportAfter(a),
                    "supportAfter(" + a + ") of " + SeqMiningTestUtils.join(pattern, len));
        }
    }
}
