/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.search.Searches.firstFail;


public class AllDifferentTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest1(CPSolver cp) {

        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 5, 5);

        cp.post(CPFactory.allDifferent(x));
        cp.post(CPFactory.equal(x[0], 0));
        for (int i = 1; i < x.length; i++) {
            assertEquals(4, x[i].size());
            assertEquals(1, x[i].min());
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest2(CPSolver cp) {

        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 5, 5);

        cp.post(CPFactory.allDifferent(x));

        SearchStatistics stats = CPFactory.makeDfs(cp, firstFail(x)).solve();
        assertEquals(120, stats.numberOfSolutions());

    }

}
