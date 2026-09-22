/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.modeling;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.modeling.Factory.*;

/**
 * Tests for the modeling-level {@code noOverlap} with position variables.
 */
public class NoOverlapWithPositionModelingTest {

    @Test
    public void testNoOverlapWithPosition3NoTransition() {
        ModelDispatcher model = makeModelDispatcher();
        int n = 3;

        IntervalVar[] intervals = new IntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = model.intervalVar(0, n, 1, true);
        }

        IntVar[] posOfInterval = model.intVarArray(n, n);
        IntVar[] intervalInPos = model.intVarArray(n, n);

        model.add(noOverlap(intervals, posOfInterval, intervalInPos));

        ConcreteCPModel cp = model.cpInstantiate();

        DFSearch dfs = cp.dfSearch(() -> {
            // firstFail on position variables
            IntExpression[] selected = new IntExpression[1];
            int minSize = Integer.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (!posOfInterval[i].isFixed() && posOfInterval[i].size() < minSize) {
                    minSize = posOfInterval[i].size();
                    selected[0] = posOfInterval[i];
                }
            }
            if (selected[0] == null) return org.maxicp.search.Searches.EMPTY;
            int v = selected[0].min();
            return org.maxicp.search.Searches.branch(
                    () -> cp.getModelProxy().add(eq(selected[0], v)),
                    () -> cp.getModelProxy().add(neq(selected[0], v)));
        });

        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                assertTrue(posOfInterval[i].isFixed());
                assertTrue(intervalInPos[i].isFixed());
                assertEquals(i, intervalInPos[posOfInterval[i].min()].min());
                assertEquals(i, posOfInterval[intervalInPos[i].min()].min());
            }
        });

        SearchStatistics stats = dfs.solve();
        assertEquals(6, stats.numberOfSolutions());
    }

    @Test
    public void testNoOverlapWithPosition4WithTransition() {
        ModelDispatcher model = makeModelDispatcher();
        int n = 4;
        int[][] trans = {
                {0, 1, 1, 1},
                {1, 0, 1, 1},
                {1, 1, 0, 1},
                {1, 1, 1, 0}
        };

        IntervalVar[] intervals = new IntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = model.intervalVar(0, 20, 2, true);
        }

        IntVar[] posOfInterval = model.intVarArray(n, n);
        IntVar[] intervalInPos = model.intVarArray(n, n);

        model.add(noOverlap(intervals, posOfInterval, intervalInPos, trans));

        ConcreteCPModel cp = model.cpInstantiate();

        DFSearch dfs = cp.dfSearch(() -> {
            IntExpression[] selected = new IntExpression[1];
            int minSize = Integer.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (!posOfInterval[i].isFixed() && posOfInterval[i].size() < minSize) {
                    minSize = posOfInterval[i].size();
                    selected[0] = posOfInterval[i];
                }
            }
            if (selected[0] == null) return org.maxicp.search.Searches.EMPTY;
            int v = selected[0].min();
            return org.maxicp.search.Searches.branch(
                    () -> cp.getModelProxy().add(eq(selected[0], v)),
                    () -> cp.getModelProxy().add(neq(selected[0], v)));
        });

        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                assertTrue(posOfInterval[i].isFixed());
                assertTrue(intervalInPos[i].isFixed());
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    int posA = posOfInterval[i].min();
                    int posB = posOfInterval[j].min();
                    if (posA < posB) {
                        assertTrue(intervals[i].endMin() + trans[i][j] <= intervals[j].startMin());
                    } else {
                        assertTrue(intervals[j].endMin() + trans[j][i] <= intervals[i].startMin());
                    }
                }
            }
        });

        SearchStatistics stats = dfs.solve();
        assertEquals(24, stats.numberOfSolutions());
    }

    @Test
    public void testNoOverlapWithPositionPropagation() {
        ModelDispatcher model = makeModelDispatcher();

        IntervalVar a = model.intervalVar(0, 10, 2, true);
        IntervalVar b = model.intervalVar(0, 10, 2, true);
        IntervalVar c = model.intervalVar(0, 10, 2, true);

        int[][] times = {
                {0, 2, 1},
                {2, 0, 1},
                {1, 1, 0}
        };

        IntVar[] posOfInterval = model.intVarArray(3, 3);
        IntVar[] intervalInPos = model.intVarArray(3, 3);

        model.add(noOverlap(new IntervalVar[]{a, b, c}, posOfInterval, intervalInPos, times));
        model.add(eq(posOfInterval[0], 0));

        ConcreteCPModel cp = model.cpInstantiate();

        assertEquals(0, a.startMin());
    }
}
