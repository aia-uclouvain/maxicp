/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.maxicp.search.Searches.staticOrder;


public class AtLeastNValueDCTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void atLeastNValueTest1(CPSolver cp) {

        int n = 4;
        CPIntVar[] Xs = CPFactory.makeIntVarArray(cp, n, 0, 3);
        CPIntVar y = CPFactory.makeIntVar(cp, 2, 5);

        cp.post(CPFactory.eq(Xs[0],0));
        cp.post(CPFactory.eq(Xs[1],0));
        cp.post(CPFactory.eq(Xs[2],0));
        cp.post(new AtLeastNValueDC(Xs, y));

        assertFalse(Xs[3].contains(0));
        assertEquals(3, Xs[3].size());
        assertEquals(2, y.max());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void atLeastNValueTest2(CPSolver cp) {

        int n = 4;
        CPIntVar[] Xs = new CPIntVar[] {
                CPFactory.makeIntVar(cp, Set.of(-5,5)),
                CPFactory.makeIntVar(cp, Set.of(-5,5)),
                CPFactory.makeIntVar(cp, Set.of(-5,5)),
                CPFactory.makeIntVar(cp, Set.of(-9,-4,-5,0,4,6,10))
        };
        CPIntVar y = CPFactory.makeIntVar(cp, 3, 5);

        cp.post(new AtLeastNValueDC(Xs, y));

        assertFalse(Xs[3].contains(-5));
        assertEquals(6, Xs[3].size());
        assertEquals(3, y.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void atLeastNValueTest3(CPSolver cp) {

        // should collect the same number of solutions as the AtLeastNValueFWC but without fails

        int n = 4;
        CPIntVar[] Xs = new CPIntVar[] {
                CPFactory.makeIntVar(cp, Set.of(-1,4,5)),
                CPFactory.makeIntVar(cp, Set.of(-1,4,5)),
                CPFactory.makeIntVar(cp, Set.of(-1,4,5)),
                CPFactory.makeIntVar(cp, Set.of(-1,4,5))
        };
        CPIntVar y = CPFactory.makeIntVar(cp, 3, 5);

        DFSearch dfSearch = CPFactory.makeDfs(cp, staticOrder(Xs));

        cp.getStateManager().saveState();

        cp.post(new AtLeastNValueDC(Xs, y));

        SearchStatistics stats1 = dfSearch.solve();
        int nSolutions1 = stats1.numberOfSolutions();

        cp.getStateManager().restoreState();

        cp.post(new AtLeastNValueFWC(Xs, y));

        SearchStatistics stats2 = dfSearch.solve();
        int nSolutions2 = stats2.numberOfSolutions();

        assertEquals(nSolutions1, nSolutions2);

        assertEquals(0, stats2.numberOfFailures());

    }





}
