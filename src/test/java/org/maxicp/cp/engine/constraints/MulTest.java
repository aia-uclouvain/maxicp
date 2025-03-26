/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MulTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, -10, 10);
        CPIntVar y = CPFactory.makeIntVar(cp, Set.of(-70, -50, 50, 70));
        CPIntVar z = CPFactory.makeIntVar(cp, 100, 100);

        cp.post(new MulVar(x, y, z)); // should post a MulCteRes because z is fixed

        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFail(x, y));
        search.onSolution(() -> {
            assertTrue(x.isFixed() && y.isFixed());
            assertTrue(((x.min() == -2) && (y.min() == -50)) ||  ((x.min() == 2)  && (y.min() == 50)));
        });

        SearchStatistics stats = search.solve();
        assertEquals(2, stats.numberOfSolutions());

    }

}