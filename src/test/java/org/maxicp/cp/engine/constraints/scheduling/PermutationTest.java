/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;
import static org.junit.jupiter.api.Assertions.*;

class PermutationTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testPermutation3NoTransition(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = makeIntervalVarArray(cp, n);
        for (int i = 0; i < n; i++) {
            intervals[i].setLength(1);
            intervals[i].setPresent();
            intervals[i].setEndMax(n);
        }

        Permutation perm = new Permutation(intervals);
        cp.post(perm);

        DFSearch dfs = makeDfs(cp, setTimes(intervals));
        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    assertTrue(intervals[i].endMin() <= intervals[j].startMin() ||
                            intervals[j].endMin() <= intervals[i].startMin());
                }
                assertTrue(perm.posOfInterval[i].isFixed());
                assertTrue(perm.intervalInPos[i].isFixed());
                assertEquals(i, perm.intervalInPos[perm.posOfInterval[i].min()].min());
                assertEquals(i, perm.posOfInterval[perm.intervalInPos[i].min()].min());
            }
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(6, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testPermutation4NoTransition(CPSolver cp) {
        int n = 4;
        CPIntervalVar[] intervals = makeIntervalVarArray(cp, n);
        for (int i = 0; i < n; i++) {
            intervals[i].setLength(1);
            intervals[i].setPresent();
            intervals[i].setEndMax(n);
        }

        Permutation perm = new Permutation(intervals);
        cp.post(perm);

        DFSearch dfs = makeDfs(cp, setTimes(intervals));
        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    assertTrue(intervals[i].endMin() <= intervals[j].startMin() ||
                            intervals[j].endMin() <= intervals[i].startMin());
                }
                assertTrue(perm.posOfInterval[i].isFixed());
                assertTrue(perm.intervalInPos[i].isFixed());
                assertEquals(i, perm.intervalInPos[perm.posOfInterval[i].min()].min());
                assertEquals(i, perm.posOfInterval[perm.intervalInPos[i].min()].min());
            }
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(24, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testPermutation3TransitionCost(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = makeIntervalVarArray(cp, n);
        for (int i = 0; i < n; i++) {
            intervals[i].setLength(1);
            intervals[i].setPresent();
            intervals[i].setEndMax(6);
        }

        int[][] transitionCost = {
                {0, 1, 2},
                {1, 0, 1},
                {2, 1, 0}
        };

        Permutation perm = new Permutation(intervals, transitionCost);
        cp.post(perm);

        CPIntVar cost = perm.transitionCost(transitionCost);

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
                expectedCost += transitionCost[from][to];
            }
            assertTrue(cost.isFixed());
            assertEquals(expectedCost, cost.min());
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(6, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testPermutationPositionBranching(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = makeIntervalVarArray(cp, n);
        for (int i = 0; i < n; i++) {
            intervals[i].setLength(1);
            intervals[i].setPresent();
            intervals[i].setEndMax(n);
        }

        Permutation perm = new Permutation(intervals);
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
        assertEquals(6, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testPermutationBinaryPropagation(CPSolver cp) {
        CPIntervalVar a = makeIntervalVar(cp, false, 2, 2);
        CPIntervalVar b = makeIntervalVar(cp, false, 2, 2);
        a.setEndMax(10);
        b.setEndMax(10);

        int[][] times = {
                {0, 2},
                {2, 0}
        };

        Permutation perm = new Permutation(new CPIntervalVar[]{a, b}, times);
        cp.post(perm);

        cp.post(eq(perm.posOfInterval[0], 0));

        assertEquals(0, a.startMin());
        assertEquals(4, b.startMin());
        assertEquals(6, b.endMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testPermutation4WithTransitionTimes(CPSolver cp) {
        int n = 4;
        CPIntervalVar[] intervals = makeIntervalVarArray(cp, n);
        for (int i = 0; i < n; i++) {
            intervals[i].setLength(2);
            intervals[i].setPresent();
            intervals[i].setEndMax(20);
        }

        int[][] times = {
                {0, 1, 1, 1},
                {1, 0, 1, 1},
                {1, 1, 0, 1},
                {1, 1, 1, 0}
        };

        Permutation perm = new Permutation(intervals, times);
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
                        assertTrue(endA + times[i][j] <= startB);
                    } else {
                        assertTrue(endB + times[j][i] <= startA);
                    }
                }
            }
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(24, stats.numberOfSolutions());
    }
}
