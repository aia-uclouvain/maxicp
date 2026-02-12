package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;
import org.maxicp.search.Searches;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.eq;
import static org.maxicp.cp.CPFactory.neq;
import static org.junit.jupiter.api.Assertions.assertEquals;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class AmongTest extends CPSolverTest {

    Random r = new Random(0);

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInterSize(CPSolver cp) {
        Set<Integer> vals = Set.of(1, 2, 3);
        CPIntVar N = CPFactory.makeIntVar(cp, Set.of(1));
        CPIntVar[] x = new CPIntVar[3];
        x[0] = CPFactory.makeIntVar(cp, 0, 5);
        x[1] = CPFactory.makeIntVar(cp, 3, 5);
        x[2] = CPFactory.makeIntVar(cp, 4, 5);

        Among constraint = new Among(x, vals, N);
        assertEquals(3, constraint.interSize(x[0]));
        assertEquals(1, constraint.interSize(x[1]));
        assertEquals(0, constraint.interSize(x[2]));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void amongTest1(CPSolver cp) {
        Set<Integer> vals = Set.of(1, 2, 3);
        CPIntVar N = CPFactory.makeIntVar(cp, Set.of(1));
        CPIntVar[] x = new CPIntVar[5];
        for (int i = 0; i < x.length; i++) {
            x[i] = CPFactory.makeIntVar(cp, 1, 5);
        }

        cp.post(new Among(x, vals, N));
        x[0].fix(1);

        cp.fixPoint();
        assertEquals(4, x[1].min());
        assertEquals(4, x[2].min());
        assertEquals(4, x[3].min());
        assertEquals(4, x[4].min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void amongTest2(CPSolver cp) {
        Set<Integer> vals = Set.of(1, 2, 3);
        CPIntVar N = CPFactory.makeIntVar(cp, Set.of(2));
        CPIntVar[] x = new CPIntVar[5];
        for (int i = 0; i < x.length; i++) {
            x[i] = CPFactory.makeIntVar(cp, 1, 4);
        }

        cp.post(CPFactory.among(x, vals, N));
        x[0].fix(4);

        cp.fixPoint();
        assertEquals(4, x[1].size());
        assertEquals(4, x[2].size());
        assertEquals(4, x[3].size());
        assertEquals(4, x[4].size());
        x[1].fix(4);

        cp.fixPoint();
        assertEquals(4, x[2].size());
        assertEquals(4, x[3].size());
        assertEquals(4, x[4].size());
        x[2].fix(4);

        cp.fixPoint();
        assertEquals(3, x[3].size());
        assertEquals(3, x[4].size());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void amongTest3(CPSolver cp) {
        Set<Integer> vals = Set.of(1, 2, 3);
        CPIntVar N = CPFactory.makeIntVar(cp, Set.of(1));
        CPIntVar[] x = new CPIntVar[5];
        for (int i = 0; i < x.length; i++) {
            x[i] = CPFactory.makeIntVar(cp, 1, 4);
        }

        cp.post(CPFactory.among(x, vals, N));

        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));
        dfs.onSolution(() -> {
            int count = 0;
            for (CPIntVar xi : x) {
                if (vals.contains(xi.min())) count++;
            }
            assertEquals(N.min(), count);
        });
        SearchStatistics stats = dfs.solve();
        assertEquals(15, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void amongTest4(CPSolver cp) {
        // assert that when N is fixed because of the values of the variables, the constraint does not fail when trying to remove values
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 5, 5);
        CPIntVar lim = CPFactory.makeIntVar(cp,1,3);
        cp.post(new Among(x, new HashSet<>(Arrays.asList(0,1)), lim));
        cp.post(eq(x[0],0));
        cp.post(eq(x[1],0));

        cp.post(neq(x[2],0));
        cp.post(neq(x[2],1));
        cp.post(eq(x[2],2));

        cp.post(neq(x[3],0));
        cp.post(neq(x[3],1));
        cp.post(eq(x[3],2));

        cp.post(neq(x[4],0));
        assertDoesNotThrow(() -> cp.post(neq(x[4],1)));
    }

}
