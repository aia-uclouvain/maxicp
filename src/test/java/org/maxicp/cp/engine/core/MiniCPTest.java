/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.search.Searches.*;


public class MiniCPTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSolveSubjectTo(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 2);

        DFSearch dfs = CPFactory.makeDfs(cp, firstFail(x));


        SearchStatistics stats1 = dfs.solveSubjectTo(l -> false, () -> {
            cp.post(CPFactory.eq(x[0], 0));
        });

        assertEquals(4, stats1.numberOfSolutions());

        SearchStatistics stats2 = dfs.solve(l -> false);

        assertEquals(8, stats2.numberOfSolutions());


    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDFS(CPSolver cp) {
        CPIntVar[] values = CPFactory.makeIntVarArray(cp, 3, 2);

        DFSearch dfs = CPFactory.makeDfs(cp, () -> {
            int sel = -1;
            for (int i = 0; i < values.length; i++)
                if (values[i].size() > 1 && sel == -1)
                    sel = i;
            final int i = sel;
            if (i == -1)
                return EMPTY;
            else return branch(() -> cp.post(CPFactory.eq(values[i], 0)),
                    () -> cp.post(CPFactory.eq(values[i], 1)));
        });


        SearchStatistics stats = dfs.solve();

        assertEquals(8,stats.numberOfSolutions());
        assertEquals(0,stats.numberOfFailures());
        assertEquals((8 + 4 + 2),stats.numberOfNodes());
    }


}
