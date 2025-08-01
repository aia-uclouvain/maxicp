package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.eq;
import static org.maxicp.search.Searches.firstFail;

public class InversePermTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTestOnRemove(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 3);
        CPIntVar[] y = CPFactory.makeIntVarArray(cp, 3, 3);
        x[0].remove(2);
        y[1].remove(1);

        cp.post(new InversePerm(x, y));

        assertFalse(y[2].contains(0));
        assertFalse(x[1].contains(1));

        cp.post(eq(x[0], 0));

        assertEquals(y[0].min(), 0);

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAllDiff(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 3);
        CPIntVar[] y = CPFactory.makeIntVarArray(cp, 3, 3);
        cp.post(new InversePerm(x, y));
        DFSearch dfs = CPFactory.makeDfs(cp, firstFail(x));
        dfs.onSolution(() -> {
            for (int i = 0; i < x.length; i++) {
                assertTrue(y[i].isFixed());
                assertEquals(x[y[i].min()].min(), i);
                assertEquals(y[x[i].min()].min(), i);
            }
        });

        assertEquals(6, dfs.solve().numberOfSolutions());
    }

}
