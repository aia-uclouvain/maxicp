/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

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
import static org.maxicp.search.Searches.and;
import static org.maxicp.search.Searches.firstFail;

public class IsSubsetTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectInconsistencyTrue(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.include(1);
        set2.exclude(1);
        b.fix(true);

        assertThrows(InconsistencyException.class, () -> cp.post(new IsSubset(b, set1, set2)));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectInconsistencyFalse(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.include(1);
        set1.excludeAll();
        set2.include(1);
        b.fix(false);

        assertThrows(InconsistencyException.class, () -> cp.post(new IsSubset(b, set1, set2)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectSubSet(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.include(1);
        set1.include(2);
        set2.include(1);
        set2.include(2);
        set1.excludeAll();

        cp.post(new IsSubset(b, set1, set2));

        assertTrue(b.isTrue());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectNotSubSet(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 10);
        CPSetVar set2 = new CPSetVarImpl(cp, 10);

        set1.include(1);
        set1.include(2);
        set2.include(1);
        set2.exclude(2);

        cp.post(new IsSubset(b, set1, set2));

        assertTrue(b.isFalse());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void removeOnSubset(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        b.fix(true);
        set1.include(1);
        set2.include(1);
        set2.exclude(2);

        cp.post(new IsSubset(b, set1, set2));

        assertTrue(set1.isExcluded(2));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void addOnNotSubset(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        b.fix(false);
        set1.include(1);
        set2.include(1);
        set2.include(0);
        set2.exclude(2);

        cp.post(new IsSubset(b, set1, set2));

        assertTrue(set1.isIncluded(2));
    }



    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCardinalityUpdate(CPSolver cp) {

        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        cp.post(new IsSubset(b, set1, set2));
        cp.post(CPFactory.eq(set1.card(),3));
        cp.post(CPFactory.eq(set2.card(),2));

        assertTrue(b.isFalse());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void detectInclusionWithPossible(CPSolver cp) {
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, 3);
        CPSetVar set2 = new CPSetVarImpl(cp, 3);

        set1.include(0);
        set1.exclude(2);
        set2.include(0);
        set2.include(1);

        // set 1 = I{0} P{1} E{2}
        // set 2 = I{0,1} P{2}

        cp.post(new IsSubset(b, set1, set2));

        assertTrue(b.isTrue());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test0(CPSolver cp) {
        int n = 3;
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, n);
        CPSetVar set2 = new CPSetVarImpl(cp, n);

        cp.post(new IsSubset(b, set1, set2));

        Random r = new Random(42);
        DFSearch dfs = CPFactory.makeDfs(cp, and(firstFail(b),CPSetVarTest.randomSetBranching(new CPSetVar[]{set1, set2}, r)));
        SearchStatistics stats = dfs.solve();

        assertEquals(Math.pow(4,n),stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {
        int n = 3;
        CPBoolVar b = CPFactory.makeBoolVar(cp);
        CPSetVar set1 = new CPSetVarImpl(cp, n);
        CPSetVar set2 = new CPSetVarImpl(cp, n);

        cp.post(new IsSubset(b, set1, set2));

        Random r = new Random(42);
        DFSearch dfs = CPFactory.makeDfs(cp, and(CPSetVarTest.randomSetBranching(new CPSetVar[]{set1, set2}, r),firstFail(b)));
        dfs.onSolution(() -> {
            if(b.isTrue()) {
                for(int i = 0; i < n; i++) {
                    if(set1.isIncluded(i)) {
                        assertTrue(set2.isIncluded(i));
                    }
                }
            } else {
                int includedExcluded = 0;
                for(int i = 0; i < n; i++) {
                    if(set1.isIncluded(i) && set2.isExcluded(i)) {
                        includedExcluded++;
                    }
                }
                assertTrue(includedExcluded > 0);
            }
        });
        SearchStatistics stats = dfs.solve();

        assertEquals(Math.pow(4,n),stats.numberOfSolutions());
    }


}
