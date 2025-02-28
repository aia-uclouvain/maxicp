package org.maxicp.cp.modeling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NoModelProxyTest extends CPSolverTest  {
    @ParameterizedTest
    @MethodSource("getSolver")
    public void withoutExplicitModelProxy(CPSolver solver) {
        CPIntVar x = CPFactory.makeIntVar(solver, 0, 10);
        solver.getModelProxy().add(Factory.eq(5, x));
        assertEquals(5, x.min());
        assertEquals(5, x.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void withoutExplicitModelProxySearch(CPSolver solver) {
        CPIntVar x = CPFactory.makeIntVar(solver, 0, 10);
        DFSearch search = CPFactory.makeDfs(solver, Searches.firstFail(x));
        assertEquals(11, search.solve().numberOfSolutions());
    }
}
