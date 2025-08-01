/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import be.uclouvain.solvercheck.WithSolverCheck;
import be.uclouvain.solvercheck.core.data.Domain;
import be.uclouvain.solvercheck.core.data.PartialAssignment;
import be.uclouvain.solvercheck.core.task.Checker;
import be.uclouvain.solvercheck.core.task.Filter;
import be.uclouvain.solvercheck.core.task.StatefulFilter;
import be.uclouvain.solvercheck.generators.GeneratorsDSL;
import be.uclouvain.solvercheck.generators.PartialAssignmentGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.search.Searches.*;


public class AllDifferentDCTest extends CPSolverTest implements WithSolverCheck {


    @Test
    public void testAllDiffWithSolverCheckStateless() {
        assertThat(
                forAll(GeneratorsDSL.listOf("Xs", jDom()).ofSizeBetween(1,10)).assertThat(xs ->
                        an(arcConsistent(allDiff())).isEquivalentTo(stateLessAllDiffDC())
                                .forAnyPartialAssignment().with(xs)
                )
        );
    }

    @Test
    public void testAllDiffWithSolverCheckStateFull() {
        assertThat(
                forAll(GeneratorsDSL.listOf("Xs", jDom()).ofSizeBetween(1,10)).assertThat(xs ->
                        a(stateFullAllDiffDC()).isEquivalentTo(stateful(arcConsistent(allDiff())))
                                .forAnyPartialAssignment().with(xs)
                )
        );
    }

    public GeneratorsDSL.GenDomainBuilder jDom() {
        return domain().withValuesBetween(0, 20);
    }

    public static Set<Integer> toSet(CPIntVar x) {
        Set<Integer> dom = new HashSet<>();
        for (int i = x.min(); i <= x.max(); i++) {
            if (x.contains(i)) {
                dom.add(i);
            }
        }
        return dom;
    }

    private Filter stateLessAllDiffDC() {
        return partialAssignment -> {
            CPSolver cp = CPFactory.makeSolver();
            CPIntVar[] x = new CPIntVar[partialAssignment.size()];
            for (int i = 0; i < x.length; i++) {
                x[i] = CPFactory.makeIntVar(cp, partialAssignment.get(i));
            }
            boolean inConsistent = false;
            try {
                cp.post(new AllDifferentDC(x));
            } catch (InconsistencyException e) {
                inConsistent = true;
            }
            Domain [] domains = new Domain[x.length];
            for (int i = 0; i < x.length; i++) {
                if (!inConsistent) {
                    domains[i] = Domain.from(toSet(x[i]));
                } else {
                    domains[i] = Domain.emptyDomain();
                }
            }
            return be.uclouvain.solvercheck.core.data.PartialAssignment.from(domains);
        };
    }


    private StatefulFilter stateFullAllDiffDC() {
        return SolverCheckUtils.statefulFilter(
                (solver, vars) -> new AllDifferentDC(vars)
        );
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest1(CPSolver cp) {

        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 5, 5);

        cp.post(new AllDifferentDC(x));
        cp.post(CPFactory.eq(x[0], 0));
        for (int i = 1; i < x.length; i++) {
            assertEquals(4, x[i].size());
            assertEquals(1, x[i].min());
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest2(CPSolver cp) {

        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 5, 5);

        cp.post(new AllDifferentDC(x));

        SearchStatistics stats = CPFactory.makeDfs(cp, firstFail(x)).solve();
        assertEquals(120, stats.numberOfSolutions());

    }


    private static CPIntVar makeIVar(CPSolver cp, Integer... values) {
        return CPFactory.makeIntVar(cp, new HashSet<>(Arrays.asList(values)));
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest3(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{
                makeIVar(cp, 1, 2),
                makeIVar(cp, 1, 2),
                makeIVar(cp, 1, 2, 3, 4)};

        cp.post(new AllDifferentDC(x));

        assertEquals(x[2].min(), 3);
        assertEquals(x[2].size(), 2);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest5(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{
                makeIVar(cp, 1, 2, 3, 4, 5),
                makeIVar(cp, 2),
                makeIVar(cp, 1, 2, 3, 4, 5),
                makeIVar(cp, 1),
                makeIVar(cp, 1, 2, 3, 4, 5, 6),
                makeIVar(cp, 6, 7, 8),
                makeIVar(cp, 3),
                makeIVar(cp, 6, 7, 8, 9),
                makeIVar(cp, 6, 7, 8)};

        cp.post(new AllDifferentDC(x));

        assertEquals(x[0].size(), 2);
        assertEquals(x[2].size(), 2);
        assertEquals(x[4].min(), 6);
        assertEquals(x[7].min(), 9);
        assertEquals(x[8].min(), 7);
        assertEquals(x[8].max(), 8);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest6(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{
                makeIVar(cp, 1, 2, 3, 4, 5),
                makeIVar(cp, 2, 7),
                makeIVar(cp, 1, 2, 3, 4, 5),
                makeIVar(cp, 1, 3),
                makeIVar(cp, 1, 2, 3, 4, 5, 6),
                makeIVar(cp, 6, 7, 8),
                makeIVar(cp, 3, 4, 5),
                makeIVar(cp, 6, 7, 8, 9),
                makeIVar(cp, 6, 7, 8)};

        cp.post(new AllDifferentDC(x));

        DFSearch dfs = CPFactory.makeDfs(cp, () -> {
            CPIntVar xs = selectMin(x,
                    xi -> xi.size() > 1,
                    xi -> -xi.size());
            if (xs == null)
                return EMPTY;
            else {
                int v = xs.min();
                return branch(
                        () -> {
                            cp.post(CPFactory.eq(xs, v));
                        },
                        () -> {
                            cp.post(CPFactory.neq(xs, v));
                        });
            }
        });

        SearchStatistics stats = dfs.solve();
        // GAC filter with a single constraint should have no fail
        assertEquals(0, stats.numberOfFailures());
        assertEquals(80, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest7(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{
                makeIVar(cp, 3, 4),
                makeIVar(cp, 1),
                makeIVar(cp, 3, 4),
                makeIVar(cp, 0),
                makeIVar(cp, 3, 4, 5),
                makeIVar(cp, 5, 6, 7),
                makeIVar(cp, 2, 9, 10),
                makeIVar(cp, 5, 6, 7, 8),
                makeIVar(cp, 5, 6, 7)};

        cp.post(new AllDifferentDC(x));

        assertFalse(x[4].contains(3));
        assertFalse(x[4].contains(4));
        assertFalse(x[5].contains(5));
        assertFalse(x[7].contains(5));
        assertFalse(x[7].contains(6));
        assertFalse(x[8].contains(5));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest8(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{
                makeIVar(cp, 0,2,3,5),
                makeIVar(cp, 4),
                makeIVar(cp, -1,1),
                makeIVar(cp, -4,-2,0,2,3),
                makeIVar(cp, -1)};

        cp.post(new AllDifferentDC(x));

        assertFalse(x[2].contains(-1));
    }




}
