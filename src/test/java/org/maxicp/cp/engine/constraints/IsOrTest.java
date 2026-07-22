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
import org.maxicp.cp.CPFactory;
import org.maxicp.search.Searches;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.search.Searches.firstFailBinary;

public class IsOrTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void isOr1(CPSolver cp) {
        CPBoolVar[] x = IntStream.range(0, 4).mapToObj(i -> CPFactory.makeBoolVar(cp)).toArray(CPBoolVar[]::new);

        CPBoolVar b = CPFactory.makeBoolVar(cp);
        cp.post(new IsOr(b, x));

        for (CPBoolVar xi : x) {
            assertFalse(xi.isFixed());
        }

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(x[1], 0));
        cp.post(CPFactory.eq(x[2], 0));
        cp.post(CPFactory.eq(x[3], 0));
        assertFalse(b.isFixed());
        cp.post(CPFactory.eq(x[0], 0));
        assertTrue(b.isFalse());
        cp.getStateManager().restoreState();

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(x[1], 0));
        cp.post(CPFactory.eq(x[2], 1));
        assertTrue(b.isTrue());
        cp.getStateManager().restoreState();

        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(b, 1));
        cp.post(CPFactory.eq(x[1], 0));
        cp.post(CPFactory.eq(x[2], 0));
        assertFalse(x[0].isFixed());
        cp.post(CPFactory.eq(x[3], 0));
        assertTrue(x[0].isTrue());
        cp.getStateManager().restoreState();


        cp.getStateManager().saveState();
        cp.post(CPFactory.eq(b, 0));
        assertTrue(x[0].isFalse());
        assertTrue(x[1].isFalse());
        assertTrue(x[2].isFalse());
        assertTrue(x[3].isFalse());
        cp.getStateManager().restoreState();

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void isOr2(CPSolver cp) {
        CPBoolVar[] x = IntStream.range(0, 4).mapToObj(i -> CPFactory.makeBoolVar(cp)).toArray(CPBoolVar[]::new);
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        cp.post(new IsOr(b, x));

        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));

        dfs.onSolution(() -> {
            int nTrue = 0;
            for (CPBoolVar xi : x) {
                if (xi.isTrue()) nTrue++;
            }
            assertTrue((nTrue > 0 && b.isTrue()) || (nTrue == 0 && b.isFalse()));
        });

        SearchStatistics stats = dfs.solve();
        assertEquals(16, stats.numberOfSolutions());
    }
}
