/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.util.exception.IntOverFlowException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.maxicp.cp.CPFactory.*;

import static org.junit.jupiter.api.Assertions.*;

public class SortedTest extends CPSolverTest {



    @ParameterizedTest
    @MethodSource("getSolver")
    public void sorted1(CPSolver cp) {
        Set<Integer> [] domX = new Set[]{Set.of(3,4), Set.of(1,2), Set.of(3), Set.of(2)};
        Set<Integer> [] domY = new Set[]{Set.of(1,2), Set.of(1,2,3), Set.of(2,3,4), Set.of(3,4)};
        Set<Integer> [] domO = new Set[]{Set.of(2,3), Set.of(0,1), Set.of(1,2,3), Set.of(0,1,2)};

        CPIntVar [] x = makeIntVarArray(4, i -> makeIntVar(cp, domX[i]));
        CPIntVar [] y = makeIntVarArray(4, i -> makeIntVar(cp, domY[i]));
        CPIntVar [] o = makeIntVarArray(4, i -> makeIntVar(cp, domO[i]));

        cp.post(new Sorted(x, o, y));
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void sorted2(CPSolver cp) {


        CPIntVar [] x = makeIntVarArray(cp, 4,0,3);
        CPIntVar [] y = makeIntVarArray(cp, 4,0,3);
        CPIntVar [] o = makeIntVarArray(cp, 4,0,3);

        cp.post(new Sorted(x, o, y));
        cp.post(new AllDifferentDC(x));

        DFSearch dfs = makeDfs(cp, Searches.firstFail(x));

        dfs.onSolution(() -> {
            for (int i = 0; i < 4; i++) {
                assertTrue(o[i].isFixed());
                assertTrue(y[i].isFixed());
                assertEquals(i, y[i].min());
                assertEquals(x[i].min(),y[o[i].min()].min());
            }
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(24, stats.numberOfSolutions());

    }





}
