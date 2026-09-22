/*
 * MaxiCP is under MIT License
 * Copyright (c)  2026 UCLouvain
 */

package org.maxicp.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SolveSubjectToTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void inconsistentSubjectToIsAnEmptyCompletedSearch(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVarArray(cp, 1, 3)[0];
        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));

        SearchStatistics stats = search.solveSubjectTo(s -> false, () -> cp.post(CPFactory.eq(x, 5)));

        // as optimizeSubjectTo does: the search space is empty, so it is exhausted
        assertTrue(stats.isCompleted());
        assertEquals(0, stats.numberOfSolutions());
    }
}
