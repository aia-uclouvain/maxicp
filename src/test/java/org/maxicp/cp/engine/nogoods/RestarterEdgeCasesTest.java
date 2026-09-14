/*
 * MaxiCP is under MIT License
 * Copyright (c)  2026 UCLouvain
 */

package org.maxicp.cp.engine.nogoods;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.constraints.AllDifferentFWC;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.MaxiCP;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Situations that only arise when the restarter is driven by time limits, stopped in unusual places, or called
 * repeatedly on the same solver.
 */
public class RestarterEdgeCasesTest extends CPSolverTest {

    private static DFSearch makeQueensSearch(CPSolver cp, CPIntVar[] q) {
        CPIntVar[] qL = CPFactory.makeIntVarArray(q.length, i -> CPFactory.minus(q[i], i));
        CPIntVar[] qR = CPFactory.makeIntVarArray(q.length, i -> CPFactory.plus(q[i], i));
        cp.post(new AllDifferentFWC(q));
        cp.post(new AllDifferentFWC(qL));
        cp.post(new AllDifferentFWC(qR));
        return CPFactory.makeDfs(cp, Searches.firstFailBinary(q));
    }

    private static int nBeforeConstraintPostedListeners(CPSolver cp) throws Exception {
        Field f = MaxiCP.class.getDeclaredField("beforeConstraintPostedListeners");
        f.setAccessible(true);
        return ((List<?>) f.get(cp)).size();
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void runStoppedBeforeVisitingAnyNode(CPSolver cp) {
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, 8, 8);
        DFSearch search = makeQueensSearch(cp, q);

        Restarter restarter = new Restarter(cp);
        // e.g. a time-based limit that is already reached when the run starts
        restarter.setRunLimit((global, run) -> global.nRestarts == 0);

        Restarter.RestartSearchStatistics stats = restarter.solve(search);

        assertTrue(stats.isCompleted());
        assertEquals(92, stats.numberOfSolutions());
    }
}
