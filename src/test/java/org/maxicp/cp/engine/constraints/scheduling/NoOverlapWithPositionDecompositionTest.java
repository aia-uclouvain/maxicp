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

class NoOverlapWithPositionDecompositionTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoOverlapWithPosition3NoTransition(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = makeIntervalVarArray(cp, n);
        for (int i = 0; i < n; i++) {
            intervals[i].setLength(1);
            intervals[i].setPresent();
            intervals[i].setEndMax(n);
        }

        CPIntVar[] posOfInterval = CPFactory.makeIntVarArray(cp, n, n);
        CPIntVar[] intervalInPos = CPFactory.makeIntVarArray(cp, n, n);
        NoOverlapWithPositionDecomposition perm = new NoOverlapWithPositionDecomposition(intervals, posOfInterval, intervalInPos);
        cp.post(perm);

        DFSearch dfs = makeDfs(cp, setTimes(intervals));
        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    assertTrue(intervals[i].endMin() <= intervals[j].startMin() ||
                            intervals[j].endMin() <= intervals[i].startMin());
                }
                assertTrue(posOfInterval[i].isFixed());
                assertTrue(intervalInPos[i].isFixed());
                assertEquals(i, intervalInPos[posOfInterval[i].min()].min());
                assertEquals(i, posOfInterval[intervalInPos[i].min()].min());
            }
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(6, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoOverlapWithPosition4NoTransition(CPSolver cp) {
        int n = 4;
        CPIntervalVar[] intervals = makeIntervalVarArray(cp, n);
        for (int i = 0; i < n; i++) {
            intervals[i].setLength(1);
            intervals[i].setPresent();
            intervals[i].setEndMax(n);
        }

        CPIntVar[] posOfInterval = CPFactory.makeIntVarArray(cp, n, n);
        CPIntVar[] intervalInPos = CPFactory.makeIntVarArray(cp, n, n);
        NoOverlapWithPositionDecomposition perm = new NoOverlapWithPositionDecomposition(intervals, posOfInterval, intervalInPos);
        cp.post(perm);

        DFSearch dfs = makeDfs(cp, setTimes(intervals));
        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    assertTrue(intervals[i].endMin() <= intervals[j].startMin() ||
                            intervals[j].endMin() <= intervals[i].startMin());
                }
                assertTrue(posOfInterval[i].isFixed());
                assertTrue(intervalInPos[i].isFixed());
                assertEquals(i, intervalInPos[posOfInterval[i].min()].min());
                assertEquals(i, posOfInterval[intervalInPos[i].min()].min());
            }
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(24, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoOverlapWithPosition3TransitionCost(CPSolver cp) {
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

        CPIntVar[] posOfInterval = CPFactory.makeIntVarArray(cp, n, n);
        CPIntVar[] intervalInPos = CPFactory.makeIntVarArray(cp, n, n);
        NoOverlapWithPositionDecomposition perm = new NoOverlapWithPositionDecomposition(intervals, posOfInterval, intervalInPos, transitionCost);
        cp.post(perm);

        // model transition cost externally
        CPIntVar cost = transitionCost(cp, intervalInPos, transitionCost);

        DFSearch dfs = makeDfs(cp, firstFailBinary(posOfInterval));
        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                assertTrue(posOfInterval[i].isFixed());
                assertTrue(intervalInPos[i].isFixed());
            }
            int expectedCost = 0;
            for (int i = 0; i < n - 1; i++) {
                int from = intervalInPos[i].min();
                int to = intervalInPos[i + 1].min();
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
    public void testNoOverlapWithPositionPositionBranching(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = makeIntervalVarArray(cp, n);
        for (int i = 0; i < n; i++) {
            intervals[i].setLength(1);
            intervals[i].setPresent();
            intervals[i].setEndMax(n);
        }

        CPIntVar[] posOfInterval = CPFactory.makeIntVarArray(cp, n, n);
        CPIntVar[] intervalInPos = CPFactory.makeIntVarArray(cp, n, n);
        NoOverlapWithPositionDecomposition perm = new NoOverlapWithPositionDecomposition(intervals, posOfInterval, intervalInPos);
        cp.post(perm);

        DFSearch dfs = makeDfs(cp, firstFailBinary(posOfInterval));
        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                assertTrue(posOfInterval[i].isFixed());
                assertTrue(intervalInPos[i].isFixed());
                assertEquals(i, intervalInPos[posOfInterval[i].min()].min());
                assertEquals(i, posOfInterval[intervalInPos[i].min()].min());
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
    public void testNoOverlapWithPositionBinaryPropagation(CPSolver cp) {
        CPIntervalVar a = makeIntervalVar(cp, false, 2, 2);
        CPIntervalVar b = makeIntervalVar(cp, false, 2, 2);
        a.setEndMax(10);
        b.setEndMax(10);

        int[][] times = {
                {0, 2},
                {2, 0}
        };

        CPIntVar[] posOfInterval = CPFactory.makeIntVarArray(cp, 2, 2);
        CPIntVar[] intervalInPos = CPFactory.makeIntVarArray(cp, 2, 2);
        NoOverlapWithPositionDecomposition perm = new NoOverlapWithPositionDecomposition(new CPIntervalVar[]{a, b}, posOfInterval, intervalInPos, times);
        cp.post(perm);

        cp.post(eq(posOfInterval[0], 0));

        assertEquals(0, a.startMin());
        assertEquals(4, b.startMin());
        assertEquals(6, b.endMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoOverlapWithPosition4WithTransitionTimes(CPSolver cp) {
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

        CPIntVar[] posOfInterval = CPFactory.makeIntVarArray(cp, n, n);
        CPIntVar[] intervalInPos = CPFactory.makeIntVarArray(cp, n, n);
        NoOverlapWithPositionDecomposition perm = new NoOverlapWithPositionDecomposition(intervals, posOfInterval, intervalInPos, times);
        cp.post(perm);

        DFSearch dfs = makeDfs(cp, firstFailBinary(posOfInterval));
        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                assertTrue(posOfInterval[i].isFixed());
                assertTrue(intervalInPos[i].isFixed());
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    int endA = intervals[i].endMin();
                    int startB = intervals[j].startMin();
                    int endB = intervals[j].endMin();
                    int startA = intervals[i].startMin();
                    int posA = posOfInterval[i].min();
                    int posB = posOfInterval[j].min();
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

    /**
     * Helper: creates a variable representing the total transition cost of a sequence.
     */
    private static CPIntVar transitionCost(CPSolver cp, CPIntVar[] intervalInPos, int[][] costMatrix) {
        int n = intervalInPos.length;
        int maxTransition = org.maxicp.util.Arrays.max(costMatrix);
        CPIntVar[] transitionTimes = makeIntVarArray(cp, n - 1, 0, maxTransition);
        for (int i = 0; i < n - 1; i++) {
            cp.post(eq(transitionTimes[i],
                    CPFactory.element(costMatrix, intervalInPos[i], intervalInPos[i + 1])));
        }
        return CPFactory.sum(transitionTimes);
    }
}
