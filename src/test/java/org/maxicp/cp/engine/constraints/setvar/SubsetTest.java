package org.maxicp.cp.engine.constraints.setvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.*;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.exclude;

public class SubsetTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectInconsistency(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.include(1);
        set2.exclude(1);

        assertThrows(InconsistencyException.class, () -> cp.post(new Subset(set1, set2)));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void includeSet1(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.include(1);
        cp.post(new Subset(set1, set2));

        assertTrue(set2.isIncluded(1));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void ExcludeSet2(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set2.exclude(1);
        cp.post(new Subset(set1, set2));

        assertTrue(set1.isExcluded(1));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectInconsistencyCard(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.card().removeBelow(5);
        set2.card().removeAbove(4);

        assertThrows(InconsistencyException.class, () -> cp.post(new Subset(set1, set2)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void deactivateOnFixSet1(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        set1.includeAll();

        CPConstraint c = new Subset(set1, set2);

        cp.post(c);

        assertFalse(c.isActive());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void deactivateOnFixSet2(CPSolver cp) {
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        set2.includeAll();

        CPConstraint c = new Subset(set1, set2);

        cp.post(c);

        assertFalse(c.isActive());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void test0(CPSolver cp) {
        int n = 3;
        CPSetVar set1 = new CPSetVarImpl(cp, n);
        CPSetVar set2 = new CPSetVarImpl(cp, n);

        cp.post(new Subset(set1, set2));

        Random r = new Random(0);
        DFSearch dfs = CPFactory.makeDfs(cp, CPSetVarTest.randomSetBranching(new CPSetVar[]{set1, set2}, r));
        SearchStatistics stats = dfs.solve();

        assertEquals(Math.pow(3, n), stats.numberOfSolutions());
    }

}
