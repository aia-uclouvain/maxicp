/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.mining.SeqMiningTestUtils;
import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.state.StateManager;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class CPSeqPatternVarTest extends CPSolverTest {

    // db of the PPIC paper example: s1=abcbc, s2=babc, s3=ab, s4=bcd
    private static SequenceDb toyDb() {
        return new SequenceDb("toy", new int[][]{
                {1, 2, 3, 2, 3},
                {2, 1, 2, 3},
                {1, 2},
                {2, 3, 4}});
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInitialState(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, toyDb());
        assertEquals(0, p.prefixLength());
        assertEquals(4, p.support());
        assertEquals(4, p.nCandidates());
        for (int a = 1; a <= 4; a++)
            assertTrue(p.isCandidate(a));
        assertEquals(3, p.supportAfter(1));
        assertEquals(4, p.supportAfter(2));
        assertEquals(3, p.supportAfter(3));
        assertEquals(1, p.supportAfter(4));
        assertTrue(p.canTerminate());
        assertFalse(p.isTerminated());
        assertFalse(p.isFixed());
        assertEquals(5, p.nSymbols());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInitialCandidatesAreOccurringSymbols(CPSolver cp) {
        // symbol 2 does not occur in the database
        SequenceDb db = new SequenceDb("gap", new int[][]{{1, 3}, {3}});
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, db);
        assertEquals(2, p.nCandidates());
        assertTrue(p.isCandidate(1));
        assertFalse(p.isCandidate(2));
        assertTrue(p.isCandidate(3));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAppend(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, toyDb());
        p.append(2);
        assertEquals(1, p.prefixLength());
        assertEquals(2, p.prefixSymbol(0));
        assertEquals(4, p.support());
        // projected suffixes: s1=[3,2,3], s2=[1,2,3], s3=[], s4=[3,4]
        assertEquals(1, p.supportAfter(1));
        assertEquals(2, p.supportAfter(2));
        assertEquals(3, p.supportAfter(3));
        assertEquals(1, p.supportAfter(4));
        assertEquals(4, p.nCandidates());

        p.append(3);
        assertEquals(2, p.prefixLength());
        assertEquals(3, p.prefixSymbol(1));
        int[] prefix = new int[2];
        assertEquals(2, p.fillPrefix(prefix));
        assertArrayEquals(new int[]{2, 3}, prefix);
        // projected suffixes: s1=[2,3], s2=[], s4=[4]
        assertEquals(3, p.support());
        assertEquals(0, p.supportAfter(1));
        assertEquals(1, p.supportAfter(2));
        assertEquals(1, p.supportAfter(3));
        assertEquals(1, p.supportAfter(4));
        assertEquals(3, p.nCandidates());
        assertFalse(p.isCandidate(1));
        assertEquals("⟨2 3⟩ +3 candidates, support=3", p.toString());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAppendNonCandidateFails(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, toyDb());
        assertThrows(InconsistencyException.class, () -> p.append(5)); // out of range
        p.excludeNext(4);
        assertThrows(InconsistencyException.class, () -> p.append(4));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testExcludeNext(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, toyDb());
        p.excludeNext(3);
        assertEquals(3, p.nCandidates());
        assertFalse(p.isCandidate(3));
        p.excludeNext(3); // no-op
        assertEquals(3, p.nCandidates());
        // the exclusion applies to the current prefix only
        p.append(2);
        assertTrue(p.isCandidate(3));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAutoTerminateOnEmptyFrontier(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, toyDb());
        for (int a = 1; a <= 4; a++)
            p.excludeNext(a);
        assertTrue(p.isTerminated());
        assertTrue(p.isFixed());
        assertEquals(0, p.nCandidates());
        assertEquals(4, p.support());
        p.excludeNext(1); // no-op once terminated
        assertTrue(p.isTerminated());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAutoTerminateOnAppend(CPSolver cp) {
        // appending the last symbol of the longest sequence empties the frontier
        SequenceDb db = new SequenceDb("single", new int[][]{{1, 2}});
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, db);
        p.append(2); // suffix is empty: no candidate left
        assertTrue(p.isTerminated());
        assertEquals(1, p.support());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTerminate(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, toyDb());
        p.append(1);
        p.terminate();
        assertTrue(p.isTerminated());
        assertTrue(p.isFixed());
        assertTrue(p.canTerminate());
        assertEquals(0, p.nCandidates());
        assertEquals(3, p.support());
        p.terminate(); // no-op
        assertThrows(InconsistencyException.class, () -> p.append(2));
        assertThrows(InconsistencyException.class, p::forbidTerminate);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testForbidTerminate(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, toyDb());
        p.forbidTerminate();
        assertFalse(p.canTerminate());
        assertThrows(InconsistencyException.class, p::terminate);
        p.forbidTerminate(); // no-op
        // appending strictly extends the prefix: the restriction is lifted
        p.append(2);
        assertTrue(p.canTerminate());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testForbidTerminateThenEmptyFrontierFails(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, toyDb());
        p.forbidTerminate();
        p.excludeNext(1);
        p.excludeNext(2);
        p.excludeNext(3);
        assertThrows(InconsistencyException.class, () -> p.excludeNext(4));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTerminatedAtConstructionOnEmptySequences(CPSolver cp) {
        SequenceDb db = new SequenceDb("empty", new int[][]{{}, {}});
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, db);
        assertTrue(p.isTerminated());
        assertEquals(2, p.support());
        assertEquals(0, p.nCandidates());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEvents(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, toyDb());
        AtomicInteger domCount = new AtomicInteger();
        AtomicInteger fixCount = new AtomicInteger();
        p.propagateOnDomainChange(new AbstractCPConstraint(cp) {
            @Override
            public void propagate() {
                domCount.incrementAndGet();
            }
        });
        p.propagateOnFix(new AbstractCPConstraint(cp) {
            @Override
            public void propagate() {
                fixCount.incrementAndGet();
            }
        });
        p.excludeNext(4);
        cp.fixPoint();
        assertEquals(1, domCount.get());
        assertEquals(0, fixCount.get());
        p.excludeNext(4); // no-op: no event
        cp.fixPoint();
        assertEquals(1, domCount.get());
        p.append(2);
        cp.fixPoint();
        assertEquals(2, domCount.get());
        assertEquals(0, fixCount.get());
        p.forbidTerminate();
        cp.fixPoint();
        assertEquals(3, domCount.get());
        p.append(3);
        cp.fixPoint();
        assertEquals(4, domCount.get());
        assertEquals(0, fixCount.get());
        p.terminate();
        cp.fixPoint();
        assertEquals(5, domCount.get());
        assertEquals(1, fixCount.get());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEventsOnAutoTerminate(CPSolver cp) {
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, toyDb());
        AtomicInteger fixCount = new AtomicInteger();
        p.propagateOnFix(new AbstractCPConstraint(cp) {
            @Override
            public void propagate() {
                fixCount.incrementAndGet();
            }
        });
        for (int a = 1; a <= 4; a++) {
            p.excludeNext(a);
            cp.fixPoint();
        }
        assertTrue(p.isTerminated());
        assertEquals(1, fixCount.get());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSaveRestore(CPSolver cp) {
        SequenceDb db = toyDb();
        CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, db);
        StateManager sm = cp.getStateManager();

        Snapshot root = snapshot(p);
        sm.saveState();
        p.append(2);
        checkKernel(p, db);
        Snapshot afterB = snapshot(p);
        sm.saveState();
        p.append(3);
        p.excludeNext(2);
        checkKernel(p, db);
        sm.restoreState();
        assertSnapshot(afterB, p);
        checkKernel(p, db);
        sm.saveState();
        p.append(2); // different branch reusing the projection trail
        checkKernel(p, db);
        sm.restoreState();
        assertSnapshot(afterB, p);
        sm.restoreState();
        assertSnapshot(root, p);
        checkKernel(p, db);
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testSaveRestoreRandomWalk(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 5; seed++) {
            CPSolver cp = solverSupplier.get();
            Random rand = new Random(seed);
            SequenceDb db = SeqMiningTestUtils.randomDb(rand, 1 + rand.nextInt(6), 2 + rand.nextInt(4), 5);
            CPSeqPatternVar p = new CPSeqPatternVarImpl(cp, db);
            StateManager sm = cp.getStateManager();
            Deque<Snapshot> stack = new ArrayDeque<>();
            int[] candidates = new int[p.nSymbols()];
            for (int step = 0; step < 200; step++) {
                checkKernel(p, db);
                double r = rand.nextDouble();
                if (!stack.isEmpty() && (r < 0.3 || p.isTerminated())) {
                    sm.restoreState();
                    assertSnapshot(stack.pop(), p);
                } else if (!p.isTerminated()) {
                    stack.push(snapshot(p));
                    sm.saveState();
                    int n = p.fillCandidates(candidates);
                    int a = candidates[rand.nextInt(n)];
                    if (r < 0.65)
                        p.append(a);
                    else
                        p.excludeNext(a);
                } else {
                    break; // terminated at the root
                }
            }
            while (!stack.isEmpty()) {
                sm.restoreState();
                assertSnapshot(stack.pop(), p);
                checkKernel(p, db);
            }
        }
    }

    private record Snapshot(int[] prefix, int support, int[] supportAfter, Set<Integer> candidates,
                            boolean terminated, boolean canTerminate) {
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
        return new Snapshot(prefix, p.support(), supportAfter, candidates, p.isTerminated(), p.canTerminate());
    }

    private static void assertSnapshot(Snapshot s, CPSeqPatternVar p) {
        int[] prefix = new int[p.prefixLength()];
        p.fillPrefix(prefix);
        assertArrayEquals(s.prefix(), prefix);
        assertEquals(s.support(), p.support());
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
     * Checks the PPIC kernel against brute-force subsequence counting.
     */
    private static void checkKernel(CPSeqPatternVar p, SequenceDb db) {
        int[] pattern = new int[db.maxSeqLength() + 1];
        int len = p.fillPrefix(pattern);
        assertEquals(SeqMiningTestUtils.subsequenceSupport(db, pattern, len), p.support(),
                "support of " + SeqMiningTestUtils.join(pattern, len));
        for (int a = 1; a < db.nSymbols(); a++) {
            pattern[len] = a;
            assertEquals(SeqMiningTestUtils.subsequenceSupport(db, pattern, len + 1), p.supportAfter(a),
                    "supportAfter(" + a + ") of " + SeqMiningTestUtils.join(pattern, len));
        }
    }
}
