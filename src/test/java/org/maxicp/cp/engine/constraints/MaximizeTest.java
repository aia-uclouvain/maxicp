/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class MaximizeTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void maximizeTest() {
        CPSolver cp = CPFactory.makeSolver();
        CPIntVar y = CPFactory.makeIntVar(cp, 10, 20);

        DFSearch dfs = CPFactory.makeDfs(cp, () -> {
            if (y.isFixed())
                return EMPTY;
            else {
                int v = y.min();
                return branch(() -> cp.post(CPFactory.eq(y, v)),
                        () -> cp.post(CPFactory.neq(y, v)));
            }
        });
        Objective obj = cp.maximize(y);
        SearchStatistics stats = dfs.optimize(obj);

        assertEquals(11, stats.numberOfSolutions());
    }


}
