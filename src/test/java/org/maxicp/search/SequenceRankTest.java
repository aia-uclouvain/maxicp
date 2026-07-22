/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

/**
 * Unit tests for {@link SequenceRank}, a sequence-based ranking search.
 *
 * <p>The tests use unit-time activities and a {@code noOverlap} constraint that channels
 * the sequences and the activities. The ranking of the sequences should induce that
 * start times are propagated accordingly:
 * <ul>
 *   <li>1 machine × 3 activities → 6 permutations (3! = 6)</li>
 *   <li>2 machines × 3 activities each → 36 solutions (6² = 36)</li>
 * </ul>
 */
class SequenceRankTest extends CPSolverTest {

    private CPIntervalVar[] makeUnitIntervals(CPSolver cp, int n, int endMax) {
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, false, 1, 1);
            intervals[i].setEndMax(endMax);
        }
        return intervals;
    }

    private CPSeqVar makeSequence(CPSolver cp, int nActivities) {
        CPSeqVar seq = CPFactory.makeSeqVar(cp, nActivities + 2, nActivities, nActivities + 1);
        for (int i = 0; i < nActivities; i++) {
            seq.require(i);
        }
        return seq;
    }

    /**
     * 1 machine, 3 unit-time activities, no transition times.
     * The SequenceRank search should generate all 6 permutations.
     * For each solution, the start times must match the sequence order: 0, 1, 2.
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSingleSequenceThreeActivities(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = makeUnitIntervals(cp, n, 10);
        CPSeqVar seq = makeSequence(cp, n);
        cp.post(noOverlap(seq, intervals));

        CPIntervalVar[][] toRank = new CPIntervalVar[][]{intervals};
        CPSeqVar[] seqVars = new CPSeqVar[]{seq};

        DFSearch dfs = makeDfs(cp, SequenceRank.sequenceRank(toRank, seqVars));

        Set<String> permutations = new HashSet<>();
        dfs.onSolution(() -> {
            int[] ordered = new int[n + 2];
            int nMembers = seq.fillNode(ordered, MEMBER_ORDERED);
            assertEquals(n + 2, nMembers);
            // extract the activity order (excluding start/end sentinels)
            int[] tour = new int[n];
            for (int i = 1; i <= n; i++) {
                int activityId = ordered[i];
                assertTrue(activityId >= 0 && activityId < n, "activity id out of range");
                tour[i - 1] = activityId;
            }
            permutations.add(tour[0] + "," + tour[1] + "," + tour[2]);
            // start times must be propagated accordingly: 0, 1, 2
            for (int i = 1; i <= n; i++) {
                int activityId = ordered[i];
                assertEquals(i - 1, intervals[activityId].startMin(),
                        "start time of activity " + activityId + " should be " + (i - 1));
                assertEquals(i, intervals[activityId].endMin(),
                        "end time of activity " + activityId + " should be " + i);
            }
        });

        SearchStatistics stats = dfs.solve();
        assertEquals(6, stats.numberOfSolutions());
        assertEquals(6, permutations.size());
    }

    /**
     * 2 machines, 3 unit-time activities per machine, no transition times.
     * The SequenceRank search should generate all 36 solutions (6² = 36).
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTwoSequencesThreeActivitiesEach(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals1 = makeUnitIntervals(cp, n, 10);
        CPIntervalVar[] intervals2 = makeUnitIntervals(cp, n, 10);
        CPSeqVar seq1 = makeSequence(cp, n);
        CPSeqVar seq2 = makeSequence(cp, n);
        cp.post(noOverlap(seq1, intervals1));
        cp.post(noOverlap(seq2, intervals2));

        CPIntervalVar[][] toRank = new CPIntervalVar[][]{intervals1, intervals2};
        CPSeqVar[] seqVars = new CPSeqVar[]{seq1, seq2};

        DFSearch dfs = makeDfs(cp, SequenceRank.sequenceRank(toRank, seqVars));

        Set<String> solutions = new HashSet<>();
        dfs.onSolution(() -> {
            for (CPSeqVar seq : new CPSeqVar[]{seq1, seq2}) {
                int[] ordered = new int[n + 2];
                int nMembers = seq.fillNode(ordered, MEMBER_ORDERED);
                assertEquals(n + 2, nMembers);
            }
            // record the combined permutation
            int[] tour1 = new int[n];
            int[] tour2 = new int[n];
            int[] buf = new int[n + 2];
            seq1.fillNode(buf, MEMBER_ORDERED);
            for (int i = 1; i <= n; i++) tour1[i - 1] = buf[i];
            seq2.fillNode(buf, MEMBER_ORDERED);
            for (int i = 1; i <= n; i++) tour2[i - 1] = buf[i];
            solutions.add(tour1[0] + "" + tour1[1] + "" + tour1[2] + "|" + tour2[0] + "" + tour2[1] + "" + tour2[2]);
        });

        SearchStatistics stats = dfs.solve();
        assertEquals(36, stats.numberOfSolutions());
        assertEquals(36, solutions.size());
    }
}