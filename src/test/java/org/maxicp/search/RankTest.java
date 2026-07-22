/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

class RankTest extends CPSolverTest {

    private CPIntervalVar[] makeUnitIntervals(CPSolver cp, int n, int endMax) {
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, false, 1, 1);
            intervals[i].setEndMax(endMax);
        }
        return intervals;
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSingleSequenceThreeActivities(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = makeUnitIntervals(cp, n, 10);
        cp.post(noOverlap(intervals));

        DFSearch dfs = makeDfs(cp, new Rank(intervals));

        SearchStatistics stats = dfs.solve();
        assertEquals(6, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTwoSequencesThreeActivitiesEach(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals1 = makeUnitIntervals(cp, n, 10);
        CPIntervalVar[] intervals2 = makeUnitIntervals(cp, n, 10);
        cp.post(noOverlap(intervals1));
        cp.post(noOverlap(intervals2));

        CPIntervalVar[][] toRank = new CPIntervalVar[][]{intervals1, intervals2};

        DFSearch dfs = makeDfs(cp, new Rank(toRank));

        SearchStatistics stats = dfs.solve();
        assertEquals(36, stats.numberOfSolutions());
    }
}
