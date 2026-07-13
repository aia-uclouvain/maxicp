/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests comparing the decomposition-based {@link Permutation} and the global
 * {@link PermutationGlobal} constraints. Verifies that:
 * <ul>
 *   <li>No solution is removed by either constraint (same solution count)</li>
 *   <li>PermutationGlobal filtering is at least as strong as Permutation</li>
 * </ul>
 */
class PermutationGlobalTest extends CPSolverTest {

    /**
     * Helper: create n present intervals of given length with given endMax.
     */
    private CPIntervalVar[] makeIntervals(CPSolver cp, int n, int length, int endMax) {
        CPIntervalVar[] intervals = makeIntervalVarArray(cp, n);
        for (int i = 0; i < n; i++) {
            intervals[i].setLength(length);
            intervals[i].setPresent();
            intervals[i].setEndMax(endMax);
        }
        return intervals;
    }

    // ----------------------------------------------------------------------
    // Solution count comparison: both constraints must find the same number
    // of solutions (no solution removed).
    // ----------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSameSolutionCount3NoTransition(CPSolver cp) {
        int n = 3;
        int[][] trans = new int[n][n];

        CPIntervalVar[] intervals1 = makeIntervals(cp, n, 1, n);
        Permutation perm1 = new Permutation(intervals1);
        cp.post(perm1);

        CPIntervalVar[] intervals2 = makeIntervals(cp, n, 1, n);
        PermutationGlobal perm2 = new PermutationGlobal(intervals2);
        cp.post(perm2);

        DFSearch dfs1 = makeDfs(cp, firstFailBinary(perm1.posOfInterval));
        SearchStatistics stats1 = dfs1.solve();

        DFSearch dfs2 = makeDfs(cp, firstFailBinary(perm2.posOfInterval));
        SearchStatistics stats2 = dfs2.solve();

        assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());
        assertEquals(6, stats1.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSameSolutionCount4NoTransition(CPSolver cp) {
        int n = 4;
        int[][] trans = new int[n][n];

        CPIntervalVar[] intervals1 = makeIntervals(cp, n, 1, n);
        Permutation perm1 = new Permutation(intervals1);
        cp.post(perm1);

        CPIntervalVar[] intervals2 = makeIntervals(cp, n, 1, n);
        PermutationGlobal perm2 = new PermutationGlobal(intervals2);
        cp.post(perm2);

        DFSearch dfs1 = makeDfs(cp, firstFailBinary(perm1.posOfInterval));
        SearchStatistics stats1 = dfs1.solve();

        DFSearch dfs2 = makeDfs(cp, firstFailBinary(perm2.posOfInterval));
        SearchStatistics stats2 = dfs2.solve();

        assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());
        assertEquals(24, stats1.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSameSolutionCount3WithTransition(CPSolver cp) {
        int n = 3;
        int[][] trans = {
                {0, 1, 2},
                {1, 0, 1},
                {2, 1, 0}
        };

        CPIntervalVar[] intervals1 = makeIntervals(cp, n, 1, 10);
        Permutation perm1 = new Permutation(intervals1, trans);
        cp.post(perm1);

        CPIntervalVar[] intervals2 = makeIntervals(cp, n, 1, 10);
        PermutationGlobal perm2 = new PermutationGlobal(intervals2, trans);
        cp.post(perm2);

        DFSearch dfs1 = makeDfs(cp, firstFailBinary(perm1.posOfInterval));
        SearchStatistics stats1 = dfs1.solve();

        DFSearch dfs2 = makeDfs(cp, firstFailBinary(perm2.posOfInterval));
        SearchStatistics stats2 = dfs2.solve();

        assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());
        assertEquals(6, stats1.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSameSolutionCount4WithTransition(CPSolver cp) {
        int n = 4;
        int[][] trans = {
                {0, 1, 1, 1},
                {1, 0, 1, 1},
                {1, 1, 0, 1},
                {1, 1, 1, 0}
        };

        CPIntervalVar[] intervals1 = makeIntervals(cp, n, 2, 20);
        Permutation perm1 = new Permutation(intervals1, trans);
        cp.post(perm1);

        CPIntervalVar[] intervals2 = makeIntervals(cp, n, 2, 20);
        PermutationGlobal perm2 = new PermutationGlobal(intervals2, trans);
        cp.post(perm2);

        DFSearch dfs1 = makeDfs(cp, firstFailBinary(perm1.posOfInterval));
        SearchStatistics stats1 = dfs1.solve();

        DFSearch dfs2 = makeDfs(cp, firstFailBinary(perm2.posOfInterval));
        SearchStatistics stats2 = dfs2.solve();

        assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());
        assertEquals(24, stats1.numberOfSolutions());
    }

    // ----------------------------------------------------------------------
    // Filtering strength: PermutationGlobal should explore at most as many
    // nodes as Permutation (filtering at least as strong).
    // ----------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFilteringStrengthNoTransition(CPSolver cp) {
        int n = 5;
        CPIntervalVar[] intervals1 = makeIntervals(cp, n, 2, 15);
        Permutation perm1 = new Permutation(intervals1);
        cp.post(perm1);

        CPIntervalVar[] intervals2 = makeIntervals(cp, n, 2, 15);
        PermutationGlobal perm2 = new PermutationGlobal(intervals2);
        cp.post(perm2);

        DFSearch dfs1 = makeDfs(cp, firstFailBinary(perm1.posOfInterval));
        SearchStatistics stats1 = dfs1.solve();

        DFSearch dfs2 = makeDfs(cp, firstFailBinary(perm2.posOfInterval));
        SearchStatistics stats2 = dfs2.solve();

        assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());
        assertTrue(stats2.numberOfNodes() <= stats1.numberOfNodes(),
                "PermutationGlobal should explore at most as many nodes as Permutation");
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFilteringStrengthWithTransition(CPSolver cp) {
        int n = 5;
        int[][] trans = {
                {0, 2, 3, 4, 5},
                {2, 0, 1, 2, 3},
                {3, 1, 0, 1, 2},
                {4, 2, 1, 0, 1},
                {5, 3, 2, 1, 0}
        };

        CPIntervalVar[] intervals1 = makeIntervals(cp, n, 2, 30);
        Permutation perm1 = new Permutation(intervals1, trans);
        cp.post(perm1);

        CPIntervalVar[] intervals2 = makeIntervals(cp, n, 2, 30);
        PermutationGlobal perm2 = new PermutationGlobal(intervals2, trans);
        cp.post(perm2);

        DFSearch dfs1 = makeDfs(cp, firstFailBinary(perm1.posOfInterval));
        SearchStatistics stats1 = dfs1.solve();

        DFSearch dfs2 = makeDfs(cp, firstFailBinary(perm2.posOfInterval));
        SearchStatistics stats2 = dfs2.solve();

        assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());
        assertTrue(stats2.numberOfNodes() <= stats1.numberOfNodes(),
                "PermutationGlobal should explore at most as many nodes as Permutation");
    }

    // ----------------------------------------------------------------------
    // Domain comparison after fixPoint: PermutationGlobal domains should be
    // at least as tight as Permutation domains.
    // ----------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDomainFilteringWithTransition(CPSolver cp) {
        int n = 4;
        int[][] trans = {
                {0, 3, 2, 4},
                {3, 0, 1, 2},
                {2, 1, 0, 3},
                {4, 2, 3, 0}
        };

        CPIntervalVar[] intervals1 = makeIntervals(cp, n, 2, 25);
        Permutation perm1 = new Permutation(intervals1, trans);
        cp.post(perm1);
        cp.fixPoint();

        CPIntervalVar[] intervals2 = makeIntervals(cp, n, 2, 25);
        PermutationGlobal perm2 = new PermutationGlobal(intervals2, trans);
        cp.post(perm2);
        cp.fixPoint();

        for (int i = 0; i < n; i++) {
            assertTrue(intervals2[i].startMin() >= intervals1[i].startMin(),
                    "PermutationGlobal startMin should be >= Permutation startMin for interval " + i);
            assertTrue(intervals2[i].startMax() <= intervals1[i].startMax(),
                    "PermutationGlobal startMax should be <= Permutation startMax for interval " + i);
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDomainFilteringNoTransition(CPSolver cp) {
        int n = 4;
        CPIntervalVar[] intervals1 = makeIntervals(cp, n, 2, 12);
        Permutation perm1 = new Permutation(intervals1);
        cp.post(perm1);
        cp.fixPoint();

        CPIntervalVar[] intervals2 = makeIntervals(cp, n, 2, 12);
        PermutationGlobal perm2 = new PermutationGlobal(intervals2);
        cp.post(perm2);
        cp.fixPoint();

        for (int i = 0; i < n; i++) {
            assertTrue(intervals2[i].startMin() >= intervals1[i].startMin(),
                    "PermutationGlobal startMin should be >= Permutation startMin for interval " + i);
            assertTrue(intervals2[i].startMax() <= intervals1[i].startMax(),
                    "PermutationGlobal startMax should be <= Permutation startMax for interval " + i);
        }
    }

    // ----------------------------------------------------------------------
    // Transition cost: both constraints should give the same cost values.
    // ----------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTransitionCost(CPSolver cp) {
        int n = 3;
        int[][] trans = {
                {0, 1, 2},
                {1, 0, 1},
                {2, 1, 0}
        };

        CPIntervalVar[] intervals = makeIntervals(cp, n, 1, 10);
        PermutationGlobal perm = new PermutationGlobal(intervals, trans);
        cp.post(perm);

        CPIntVar cost = perm.transitionCost(trans);

        DFSearch dfs = makeDfs(cp, firstFailBinary(perm.posOfInterval));
        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                assertTrue(perm.posOfInterval[i].isFixed());
                assertTrue(perm.intervalInPos[i].isFixed());
            }
            int expectedCost = 0;
            for (int i = 0; i < n - 1; i++) {
                int from = perm.intervalInPos[i].min();
                int to = perm.intervalInPos[i + 1].min();
                expectedCost += trans[from][to];
            }
            assertTrue(cost.isFixed());
            assertEquals(expectedCost, cost.min());
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(6, stats.numberOfSolutions());
    }

    // ----------------------------------------------------------------------
    // Propagation test: forcing a position should propagate to start times.
    // ----------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testPropagationForceFirst(CPSolver cp) {
        CPIntervalVar a = makeIntervalVar(cp, false, 2, 2);
        CPIntervalVar b = makeIntervalVar(cp, false, 2, 2);
        a.setEndMax(10);
        b.setEndMax(10);

        int[][] times = {{0, 2}, {2, 0}};

        PermutationGlobal perm = new PermutationGlobal(new CPIntervalVar[]{a, b}, times);
        cp.post(perm);

        cp.post(eq(perm.posOfInterval[0], 0));

        assertEquals(0, a.startMin());
        assertEquals(4, b.startMin());
    }

    // ----------------------------------------------------------------------
    // Solution validation: all solutions from PermutationGlobal must satisfy
    // the no-overlap with transition times constraint.
    // ----------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSolutionValidityWithTransition(CPSolver cp) {
        int n = 4;
        int[][] times = {
                {0, 1, 1, 1},
                {1, 0, 1, 1},
                {1, 1, 0, 1},
                {1, 1, 1, 0}
        };

        CPIntervalVar[] intervals = makeIntervals(cp, n, 2, 20);
        PermutationGlobal perm = new PermutationGlobal(intervals, times);
        cp.post(perm);

        DFSearch dfs = makeDfs(cp, firstFailBinary(perm.posOfInterval));
        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                assertTrue(perm.posOfInterval[i].isFixed());
                assertTrue(perm.intervalInPos[i].isFixed());
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    int endA = intervals[i].endMin();
                    int startB = intervals[j].startMin();
                    int endB = intervals[j].endMin();
                    int startA = intervals[i].startMin();
                    int posA = perm.posOfInterval[i].min();
                    int posB = perm.posOfInterval[j].min();
                    if (posA < posB) {
                        assertTrue(endA + times[i][j] <= startB,
                                "interval " + i + " before " + j + " must respect transition time");
                    } else {
                        assertTrue(endB + times[j][i] <= startA,
                                "interval " + j + " before " + i + " must respect transition time");
                    }
                }
            }
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(24, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSolutionValidityNoTransition(CPSolver cp) {
        int n = 4;
        CPIntervalVar[] intervals = makeIntervals(cp, n, 1, n);
        PermutationGlobal perm = new PermutationGlobal(intervals);
        cp.post(perm);

        DFSearch dfs = makeDfs(cp, firstFailBinary(perm.posOfInterval));
        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                assertTrue(perm.posOfInterval[i].isFixed());
                assertTrue(perm.intervalInPos[i].isFixed());
                assertEquals(i, perm.intervalInPos[perm.posOfInterval[i].min()].min());
                assertEquals(i, perm.posOfInterval[perm.intervalInPos[i].min()].min());
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    assertTrue(intervals[i].endMin() <= intervals[j].startMin() ||
                            intervals[j].endMin() <= intervals[i].startMin());
                }
            }
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(24, stats.numberOfSolutions());
    }
}
