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
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.search.Searches.firstFail;

public class Element1DVarTest extends CPSolverTest {

    private static CPIntVar makeIVar(CPSolver cp, Integer... values) {
        return CPFactory.makeIntVar(cp, new HashSet<>(Arrays.asList(values)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dVarTest1(CPSolver cp) {
        CPIntVar y = CPFactory.makeIntVar(cp, -3, 10);
        CPIntVar z = CPFactory.makeIntVar(cp, 2, 40);

        CPIntVar[] T = new CPIntVar[]{CPFactory.makeIntVar(cp, 9, 9), CPFactory.makeIntVar(cp, 8, 8), CPFactory.makeIntVar(cp, 7, 7), CPFactory.makeIntVar(cp, 5, 5), CPFactory.makeIntVar(cp, 6, 6)};

        cp.post(new Element1DVar(T, y, z));

        assertEquals(0, y.min());
        assertEquals(4, y.max());


        assertEquals(5, z.min());
        assertEquals(9, z.max());

        z.removeAbove(7);
        cp.fixPoint();

        assertEquals(2, y.min());


        y.remove(3);
        cp.fixPoint();

        assertEquals(7, z.max());
        assertEquals(6, z.min());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dVarTest2(CPSolver cp) {
        CPIntVar y = CPFactory.makeIntVar(cp, -3, 10);
        CPIntVar z = CPFactory.makeIntVar(cp, -4, 40);

        CPIntVar[] T = new CPIntVar[]{CPFactory.makeIntVar(cp, 1, 2),
                CPFactory.makeIntVar(cp, 3, 4),
                CPFactory.makeIntVar(cp, 5, 6),
                CPFactory.makeIntVar(cp, 7, 8),
                CPFactory.makeIntVar(cp, 9, 10)};

        cp.post(new Element1DVar(T, y, z));

        assertEquals(0, y.min());
        assertEquals(4, y.max());

        assertEquals(1, z.min());
        assertEquals(10, z.max());

        y.removeAbove(2);
        cp.fixPoint();

        assertEquals(6, z.max());

        y.fix(2);
        cp.fixPoint();

        assertEquals(5, z.min());
        assertEquals(6, z.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dVarTest3(CPSolver cp) {
        CPIntVar y = CPFactory.makeIntVar(cp, -3, 10);
        CPIntVar z = CPFactory.makeIntVar(cp, -20, 40);

        CPIntVar[] T = new CPIntVar[]{CPFactory.makeIntVar(cp, 9, 9), CPFactory.makeIntVar(cp, 8, 8), CPFactory.makeIntVar(cp, 7, 7), CPFactory.makeIntVar(cp, 5, 5), CPFactory.makeIntVar(cp, 6, 6)};

        cp.post(new Element1DVar(T, y, z));

        DFSearch dfs = CPFactory.makeDfs(cp, firstFail(y, z));
        dfs.onSolution(() ->
                assertEquals(T[y.min()].min(), z.min())
        );
        SearchStatistics stats = dfs.solve();

        assertEquals(5, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void element1dVarTest4(CPSolver cp) {
        CPIntVar x0 = makeIVar(cp, 0, 1, 5);
        CPIntVar x1 = makeIVar(cp, -5, -4, -3, -2, 0, 1, 5);
        CPIntVar x2 = makeIVar(cp, -2, 0);


        cp.post(new Element1DVar(new CPIntVar[]{x0}, x1, x2));

        assertEquals(0, x0.min());
        assertEquals(0, x1.min());
        assertEquals(0, x2.min());
        assertEquals(0, x0.max());
        assertEquals(0, x1.max());
        assertEquals(0, x2.max());
    }

}
