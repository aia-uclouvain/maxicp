/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.cp.CPFactory;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.search.Searches.firstFailBinary;


public class OrTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOr1(CPSolver cp) {
        CPBoolVar[] x = IntStream.range(0, 4).mapToObj(i -> CPFactory.makeBoolVar(cp)).toArray(CPBoolVar[]::new);
        cp.post(new Or(x));

        for (CPBoolVar xi : x) {
            assertFalse(xi.isFixed());
        }

        cp.post(CPFactory.eq(x[1], 0));
        cp.post(CPFactory.eq(x[2], 0));
        cp.post(CPFactory.eq(x[3], 0));
        assertTrue(x[0].isTrue());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOr2(CPSolver cp) {
        CPBoolVar[] x = IntStream.range(0, 4).mapToObj(i -> CPFactory.makeBoolVar(cp)).toArray(CPBoolVar[]::new);
        cp.post(new Or(x));

        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));

        dfs.onSolution(() -> {
            int nTrue = 0;
            for (CPBoolVar xi : x) {
                if (xi.isTrue()) nTrue++;
            }
            assertTrue(nTrue > 0);

        });

        SearchStatistics stats = dfs.solve();

        assertEquals(15, stats.numberOfSolutions());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOr3(CPSolver cp) {
        CPBoolVar[] x = IntStream.range(0, 4).mapToObj(i -> CPFactory.makeBoolVar(cp)).toArray(CPBoolVar[]::new);
        for (CPBoolVar xi : x) {
            xi.fix(false);
        }
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new Or(x)));
    }


}
