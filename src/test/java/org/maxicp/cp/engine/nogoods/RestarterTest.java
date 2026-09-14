/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 */

package org.maxicp.cp.engine.nogoods;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.constraints.AllDifferentFWC;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestarterTest extends CPSolverTest {

    private static DFSearch makeQueensSearch(CPSolver cp, CPIntVar[] q) {
        CPIntVar[] qL = CPFactory.makeIntVarArray(q.length, i -> CPFactory.minus(q[i], i));
        CPIntVar[] qR = CPFactory.makeIntVarArray(q.length, i -> CPFactory.plus(q[i], i));

        cp.post(new AllDifferentFWC(q));
        cp.post(new AllDifferentFWC(qL));
        cp.post(new AllDifferentFWC(qR));

        return CPFactory.makeDfs(cp, Searches.firstFailBinary(q));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void nQueensWithRestarterEventuallyExploreAllSolutions(CPSolver cp) {
        int n = 8;
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, n, n);
        DFSearch search = makeQueensSearch(cp, q);

        Restarter restarter = new Restarter(cp);
        restarter.setRunLimit((global, run) -> run.numberOfNodes() >= 100);
        
        Restarter.RestartSearchStatistics stats = restarter.solve(search);

        assertTrue(stats.isCompleted());
        assertEquals(92, stats.numberOfSolutions());
        assertTrue(stats.nRestarts > 1);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void restarterWithoutNogoodsCompletes(CPSolver cp) {
        int n = 8;
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, n, n);
        DFSearch search = makeQueensSearch(cp, q);

        Restarter restarter = new Restarter(cp);
        restarter.setRunLimit(new Restarter.LubyRestart(10));

        Restarter.RestartSearchStatistics stats = restarter.solve(search, false);

        assertTrue(stats.isCompleted());
        assertTrue(stats.nRestarts > 1);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void nQueensWithNaryBranchingAndRestarterEventuallyExploreAllSolutions(CPSolver cp) {
        int n = 8;
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, n, n);
        makeQueensSearch(cp, q);
        // n-ary: the ongoing branch x = v of a node whose siblings x = v' were refuted is not implied by them
        DFSearch search = CPFactory.makeDfs(cp, Searches.heuristicNary(Searches.minDomVariableSelector(q), v -> v));

        Restarter restarter = new Restarter(cp);
        restarter.setRunLimit((global, run) -> run.numberOfNodes() >= 20);

        Restarter.RestartSearchStatistics stats = restarter.solve(search);

        assertTrue(stats.isCompleted());
        assertEquals(92, stats.numberOfSolutions());
        assertTrue(stats.nRestarts > 1);
    }
}
