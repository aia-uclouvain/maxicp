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

    @ParameterizedTest
    @MethodSource("getSolver")
    public void nogoodsThatCloseTheProblemMeanCompletion(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 2);
        // unsatisfiable, but forward checking only notices it once the variables get fixed
        cp.post(new AllDifferentFWC(x));
        DFSearch search = CPFactory.makeDfs(cp, Searches.staticOrderBinary(x));

        Restarter restarter = new Restarter(cp);
        // x0 = 0 and x0 != 0 both fail, and the run is stopped before the search notices it is done:
        // the recorded nogood x0 != 0 then fails at the root
        restarter.setRunLimit((global, run) -> run.numberOfNodes() >= 2);

        Restarter.RestartSearchStatistics stats = restarter.solve(search);

        assertTrue(stats.isCompleted());
        assertEquals(0, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void repeatedCallsDoNotAccumulateConstraintListeners(CPSolver cp) throws Exception {
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, 6, 6);
        DFSearch search = makeQueensSearch(cp, q);

        Restarter restarter = new Restarter(cp);
        restarter.setRunLimit(new Restarter.LubyRestart(10));

        restarter.solve(search);
        int afterOneCall = nBeforeConstraintPostedListeners(cp);
        restarter.solve(search);
        restarter.solve(search);

        assertEquals(afterOneCall, nBeforeConstraintPostedListeners(cp));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void branchFailingOnTheObjectiveBoundBeforeItsDecision(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVarArray(cp, 1, 2)[0];
        CPIntVar z = CPFactory.makeIntVarArray(cp, 1, 2)[0];
        CPIntVar y = CPFactory.sum(x, z);
        var objective = cp.minimize(y);
        DFSearch search = CPFactory.makeDfs(cp, Searches.staticOrderBinary(x, z));

        Restarter restarter = new Restarter(cp);
        // x = 0, z = 0 is a solution (bound -1); then z != 0 fails when the objective is filtered, before the
        // decision z != 0 is posted, and the run is stopped right after
        restarter.setRunLimit((global, run) -> run.numberOfNodes() >= 3);

        Restarter.RestartSearchStatistics stats = restarter.optimize(objective, search);

        assertTrue(stats.isCompleted());
        assertEquals(1, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void solutionAtTheRootOfARun(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 1, 2);
        DFSearch search = CPFactory.makeDfs(cp, Searches.staticOrderBinary(x));

        Restarter restarter = new Restarter(cp);
        // the first run is stopped right after its solution x = 0: the nogood x != 0 then fixes x = 1 at the root
        // of the second run, which is a solution without any decision
        restarter.setRunLimit((global, run) -> run.numberOfNodes() >= 1);

        Restarter.RestartSearchStatistics stats = restarter.solve(search);

        assertTrue(stats.isCompleted());
        assertEquals(2, stats.numberOfSolutions());
    }
}
