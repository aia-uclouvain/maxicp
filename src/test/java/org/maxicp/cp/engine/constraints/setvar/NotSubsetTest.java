package org.maxicp.cp.engine.constraints.setvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPSetVar;
import org.maxicp.cp.engine.core.CPSetVarImpl;
import org.maxicp.cp.engine.core.CPSetVarTest;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class NotSubsetTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectInconsistencyOnSubset(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.include(1);
        set1.excludeAll();
        set2.include(1);
        set2.include(2);

        assertThrows(InconsistencyException.class, () -> cp.post(new NotSubset(set1, set2)));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void addOnlyPossibleValue(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        set1.exclude(1);
        set1.exclude(2);
        set2.exclude(0);

        cp.post(new NotSubset(set1, set2));

        assertTrue(set1.isIncluded(0));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void addOnlyPossibleValue2(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        set1.include(0);
        set1.include(1);
        set2.include(0);
        set2.include(1);

        cp.post(new NotSubset(set1, set2));

        assertTrue(set1.isIncluded(2));
        assertTrue(set2.isExcluded(2));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void excludeOnlyPossibleValue(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        set1.includeAll();
        set2.include(0);
        set2.include(1);

        cp.post(new NotSubset(set1, set2));

        assertTrue(set2.isExcluded(2));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test0(CPSolver cp) {
        int n = 3;
        CPSetVar set1 = new CPSetVarImpl(cp, n);
        CPSetVar set2 = new CPSetVarImpl(cp, n);

        cp.post(new NotSubset(set1, set2));

        Random r = new Random(42);
        DFSearch dfs = CPFactory.makeDfs(cp, CPSetVarTest.randomSetBranching(new CPSetVar[]{set1, set2}, r));
        SearchStatistics stats = dfs.solve();

        assertEquals(Math.pow(4, n) - Math.pow(3, n), stats.numberOfSolutions());
    }
}
