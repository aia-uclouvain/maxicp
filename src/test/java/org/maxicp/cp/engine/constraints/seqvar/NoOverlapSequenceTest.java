/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.seqvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

/**
 * Tests for {@link NoOverlapSequence}, the constraint that channels a
 * {@link CPSeqVar} with {@link CPIntervalVar} intervals and enforces
 * no-overlap with transition times.
 */
class NoOverlapSequenceTest extends CPSolverTest {

    /**
     * Helper: create n present intervals of given length with given endMax.
     */
    private CPIntervalVar[] makeIntervals(CPSolver cp, int n, int length, int endMax) {
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, false, length, length);
            intervals[i].setEndMax(endMax);
        }
        return intervals;
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBasicNoOverlap(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = makeIntervals(cp, n, 2, 20);
        int[][] trans = new int[n][n]; // zero transitions

        CPSeqVar seq = CPFactory.makeSeqVar(cp, n + 2, n, n + 1);
        for (int i = 0; i < n; i++) {
            seq.require(i);
        }
        cp.post(new NoOverlapSequence(seq, intervals, trans));

        seq.insert(seq.start(), 0);
        cp.fixPoint();

        assertEquals(0, intervals[0].startMin());
        assertEquals(2, intervals[0].endMin());

        seq.insert(0, 1);
        cp.fixPoint();

        assertEquals(2, intervals[1].startMin());
        assertEquals(4, intervals[1].endMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTransitionTimesForward(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = makeIntervals(cp, n, 2, 30);
        int[][] trans = {
                {0, 5, 3},
                {5, 0, 2},
                {3, 2, 0}
        };

        CPSeqVar seq = CPFactory.makeSeqVar(cp, n + 2, n, n + 1);
        for (int i = 0; i < n; i++) {
            seq.require(i);
        }
        cp.post(new NoOverlapSequence(seq, intervals, trans));

        seq.insert(seq.start(), 0);
        seq.insert(0, 1);
        cp.fixPoint();

        assertEquals(0, intervals[0].startMin());
        assertEquals(7, intervals[1].startMin());
        assertEquals(9, intervals[1].endMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTransitionTimesBackward(CPSolver cp) {
        int n = 2;
        CPIntervalVar[] intervals = makeIntervals(cp, n, 3, 30);
        int[][] trans = {
                {0, 4},
                {4, 0}
        };

        CPSeqVar seq = CPFactory.makeSeqVar(cp, n + 2, n, n + 1);
        for (int i = 0; i < n; i++) {
            seq.require(i);
        }
        // Fix end dummy at time 14 (3 + 4 + 3 + 4 = 14 minimum span)
        // The dummy end interval is created internally, so we fix it via the sequence
        // by constraining the last real interval's endMax
        intervals[1].setEndMax(14);
        cp.post(new NoOverlapSequence(seq, intervals, trans));

        seq.insert(seq.start(), 0);
        seq.insert(0, 1);
        cp.fixPoint();

        // Forward: 0 at 0-3, 1 at 3+4=7, ends 10
        // Backward from end=14: 1 ends <=14, starts <=11, 0 ends <=11-4=7, starts <=4
        assertEquals(0, intervals[0].startMin());
        assertTrue(intervals[0].startMax() <= 4);
        assertEquals(7, intervals[1].startMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInsertionFiltering(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = makeIntervals(cp, n, 2, 20);
        // triangular inequality: 8+2=10 <= 10 OK
        int[][] trans = {
                {0, 10, 8},
                {10, 0, 2},
                {8, 2, 0}
        };

        intervals[1].setStartMin(0);
        intervals[1].setEndMax(5);

        CPSeqVar seq = CPFactory.makeSeqVar(cp, n + 2, n, n + 1);
        for (int i = 0; i < n; i++) {
            seq.require(i);
        }
        cp.post(new NoOverlapSequence(seq, intervals, trans));

        seq.insert(seq.start(), 0);
        cp.fixPoint();

        // Node 0 ends at 2, transition 0->1 is 10, so 1 would start at >= 12
        // But 1's endMax is 5, so inserting 1 after 0 should be filtered
        int nInsert1 = seq.nInsert(1);
        assertTrue(nInsert1 < seq.nNode());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAllSolutions3NoTransition(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = makeIntervals(cp, n, 1, 10);
        int[][] trans = new int[n][n];

        CPSeqVar seq = CPFactory.makeSeqVar(cp, n + 2, n, n + 1);
        for (int i = 0; i < n; i++) {
            seq.require(i);
        }
        cp.post(new NoOverlapSequence(seq, intervals, trans));

        DFSearch dfs = makeDfs(cp, () -> {
            int[] nodes = new int[seq.nNode()];
            int nInsertable = seq.fillNode(nodes, INSERTABLE_REQUIRED);
            if (nInsertable == 0) return EMPTY;
            int node = nodes[0];
            int[] preds = new int[seq.nNode()];
            int nPreds = seq.fillInsert(node, preds);
            int pred = preds[0];
            int succ = seq.memberAfter(pred);
            return branch(
                    () -> cp.post(CPFactory.insert(seq, pred, node)),
                    () -> cp.post(CPFactory.notBetween(seq, pred, node, succ)));
        });

        dfs.onSolution(() -> {
            int[] members = new int[seq.nNode()];
            int nMember = seq.fillNode(members, MEMBER_ORDERED);
            for (int i = 1; i < nMember; i++) {
                int prev = members[i - 1];
                int curr = members[i];
                if (prev < n && curr < n) {
                    assertTrue(intervals[prev].endMin() + trans[prev][curr] <= intervals[curr].startMin());
                }
            }
        });

        SearchStatistics stats = dfs.solve();
        assertEquals(6, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFactoryNoOverlap(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = makeIntervalVarArray(cp, n);
        for (int i = 0; i < n; i++) {
            intervals[i].setLength(1);
            intervals[i].setPresent();
            intervals[i].setEndMax(10);
        }
        int[][] trans = {
                {0, 1, 1},
                {1, 0, 1},
                {1, 1, 0}
        };

        CPSeqVar seq = CPFactory.makeSeqVar(cp, n + 2, n, n + 1);
        for (int i = 0; i < n; i++) {
            seq.require(i);
        }
        cp.post(noOverlap(seq, intervals, trans));

        DFSearch dfs = makeDfs(cp, () -> {
            int[] nodes = new int[seq.nNode()];
            int nInsertable = seq.fillNode(nodes, INSERTABLE_REQUIRED);
            if (nInsertable == 0) return EMPTY;
            int node = nodes[0];
            int[] preds = new int[seq.nNode()];
            int nPreds = seq.fillInsert(node, preds);
            int pred = preds[0];
            int succ = seq.memberAfter(pred);
            return branch(
                    () -> cp.post(CPFactory.insert(seq, pred, node)),
                    () -> cp.post(CPFactory.notBetween(seq, pred, node, succ)));
        });

        dfs.onSolution(() -> {
            int[] members = new int[seq.nNode()];
            int nMember = seq.fillNode(members, MEMBER_ORDERED);
            for (int i = 1; i < nMember; i++) {
                int prev = members[i - 1];
                int curr = members[i];
                if (prev < n && curr < n) {
                    assertTrue(intervals[prev].endMin() + trans[prev][curr] <= intervals[curr].startMin());
                }
            }
        });

        SearchStatistics stats = dfs.solve();
        assertEquals(6, stats.numberOfSolutions());
    }
}
