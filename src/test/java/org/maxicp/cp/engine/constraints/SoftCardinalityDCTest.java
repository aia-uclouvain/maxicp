/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SoftCardinalityDCTest extends CPSolverTest {

    // Brute-force: count solutions where violation is within [violMin, violMax]
    private int countSolutionsBruteForce(Set<Integer>[] domains, int minval, int[] low, int[] up, int violMin, int violMax) {
        return enumerate(domains, minval, low, up, violMin, violMax, new int[domains.length], 0);
    }

    private int enumerate(Set<Integer>[] domains, int minval, int[] low, int[] up, int violMin, int violMax, int[] tuple, int idx) {
        if (idx == tuple.length) {
            int[] occ = new int[low.length];
            for (int v : tuple) {
                int vi = v - minval;
                if (vi >= 0 && vi < occ.length) occ[vi]++;
            }
            int viol = 0;
            for (int i = 0; i < low.length; i++) {
                viol += Math.max(0, low[i] - occ[i]);
                viol += Math.max(0, occ[i] - up[i]);
            }
            return (viol >= violMin && viol <= violMax) ? 1 : 0;
        }
        int count = 0;
        for (int v : domains[idx]) {
            tuple[idx] = v;
            count += enumerate(domains, minval, low, up, violMin, violMax, tuple, idx + 1);
        }
        return count;
    }

    // ==================== Basic tests ====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testExactSatisfaction(CPSolver cp) {
        // 3 variables in {0,1,2}, each value exactly once => viol=0, 3!=6 solutions
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 0, 2);
        CPIntVar viol = CPFactory.makeIntVar(cp, 0, 0);
        cp.post(new SoftCardinalityDC(x, 0, new int[]{1, 1, 1}, new int[]{1, 1, 1}, viol));

        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));
        SearchStatistics stats = search.solve();
        assertEquals(6, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testViolationFixedToZero(CPSolver cp) {
        // Hard GCC: 4 vars in {0,1}, each value exactly 2 times => C(4,2)=6
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 4, 0, 1);
        CPIntVar viol = CPFactory.makeIntVar(cp, 0, 0);
        cp.post(new SoftCardinalityDC(x, 0, new int[]{2, 2}, new int[]{2, 2}, viol));

        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));
        SearchStatistics stats = search.solve();
        assertEquals(6, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInfeasible(CPSolver cp) {
        // 2 vars in {0}, need value 1 at least once, viol=0 => infeasible
        CPIntVar[] x = new CPIntVar[]{
                CPFactory.makeIntVar(cp, Set.of(0)),
                CPFactory.makeIntVar(cp, Set.of(0))
        };
        CPIntVar viol = CPFactory.makeIntVar(cp, 0, 0);
        assertThrowsExactly(InconsistencyException.class, () ->
                cp.post(new SoftCardinalityDC(x, 0, new int[]{1, 1}, new int[]{2, 2}, viol)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testViolLowerBoundPruning(CPSolver cp) {
        // 2 vars in {0}, low=[2,1] => can't satisfy val 1 => viol >= 1
        CPIntVar[] x = new CPIntVar[]{
                CPFactory.makeIntVar(cp, Set.of(0)),
                CPFactory.makeIntVar(cp, Set.of(0))
        };
        CPIntVar viol = CPFactory.makeIntVar(cp, 0, 10);
        cp.post(new SoftCardinalityDC(x, 0, new int[]{2, 1}, new int[]{2, 2}, viol));
        assertTrue(viol.min() >= 1);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoConstraint(CPSolver cp) {
        // low all 0, up >= n => no violation ever, all 2^3=8 solutions
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 0, 1);
        CPIntVar viol = CPFactory.makeIntVar(cp, 0, 10);
        cp.post(new SoftCardinalityDC(x, 0, new int[]{0, 0}, new int[]{3, 3}, viol));
        assertEquals(0, viol.min());

        // Fix viol to 0 and count x-assignments only
        viol.fix(0);
        cp.fixPoint();
        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));
        SearchStatistics stats = search.solve();
        assertEquals(8, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSingleVariableInfeasible(CPSolver cp) {
        // 1 var in {0,1,2}, need each value once, viol=0 => viol>=2 => fail
        CPIntVar[] x = new CPIntVar[]{CPFactory.makeIntVar(cp, 0, 2)};
        CPIntVar viol = CPFactory.makeIntVar(cp, 0, 0);
        assertThrowsExactly(InconsistencyException.class, () ->
                cp.post(new SoftCardinalityDC(x, 0, new int[]{1, 1, 1}, new int[]{1, 1, 1}, viol)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSingleVariableWithViol(CPSolver cp) {
        CPIntVar[] x = new CPIntVar[]{CPFactory.makeIntVar(cp, 0, 2)};
        CPIntVar viol = CPFactory.makeIntVar(cp, 0, 10);
        cp.post(new SoftCardinalityDC(x, 0, new int[]{1, 1, 1}, new int[]{1, 1, 1}, viol));
        assertEquals(2, viol.min()); // 2 values unsatisfied
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOverflowInfeasible(CPSolver cp) {
        // 3 vars in {0,1}, up=[1,1], viol=0 => pigeonhole fail
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 0, 1);
        CPIntVar viol = CPFactory.makeIntVar(cp, 0, 0);
        assertThrowsExactly(InconsistencyException.class, () ->
                cp.post(new SoftCardinalityDC(x, 0, new int[]{0, 0}, new int[]{1, 1}, viol)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOverflowWithViol(CPSolver cp) {
        // 3 vars in {0,1}, up=[1,1] => at least 1 overflow
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 0, 1);
        CPIntVar viol = CPFactory.makeIntVar(cp, 0, 10);
        cp.post(new SoftCardinalityDC(x, 0, new int[]{0, 0}, new int[]{1, 1}, viol));
        assertTrue(viol.min() >= 1);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDomainPruningAllDiff(CPSolver cp) {
        // AllDiff via SoftGCC: fix x[0]=0, check 0 removed from others
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 3, 0, 2);
        CPIntVar viol = CPFactory.makeIntVar(cp, 0, 0);
        cp.post(new SoftCardinalityDC(x, 0, new int[]{1, 1, 1}, new int[]{1, 1, 1}, viol));

        x[0].fix(0);
        cp.fixPoint();
        assertFalse(x[1].contains(0));
        assertFalse(x[2].contains(0));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMinvalOffset(CPSolver cp) {
        // Non-zero minval: values 5,6,7, each exactly once
        CPIntVar[] x = new CPIntVar[]{
                CPFactory.makeIntVar(cp, Set.of(5, 6, 7)),
                CPFactory.makeIntVar(cp, Set.of(5, 6, 7)),
                CPFactory.makeIntVar(cp, Set.of(5, 6, 7))
        };
        CPIntVar viol = CPFactory.makeIntVar(cp, 0, 0);
        cp.post(new SoftCardinalityDC(x, 5, new int[]{1, 1, 1}, new int[]{1, 1, 1}, viol));

        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));
        SearchStatistics stats = search.solve();
        assertEquals(6, stats.numberOfSolutions());
    }

    // ==================== Domain consistency: brute force comparison ====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDomainConsistencySmall(CPSolver cp) {
        @SuppressWarnings("unchecked")
        Set<Integer>[] domains = new Set[]{Set.of(0, 1, 2), Set.of(0, 1), Set.of(1, 2), Set.of(0, 2)};
        int[] low = {1, 1, 1};
        int[] up = {2, 2, 2};

        CPIntVar[] x = CPFactory.makeIntVarArray(domains.length, i -> CPFactory.makeIntVar(cp, domains[i]));
        CPIntVar viol = CPFactory.makeIntVar(cp, 0, 10);
        cp.post(new SoftCardinalityDC(x, 0, low, up, viol));

        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));
        SearchStatistics stats = search.solve();

        int expected = countSolutionsBruteForce(domains, 0, low, up, 0, 10);
        assertEquals(expected, stats.numberOfSolutions());
    }

    // ==================== Random tests ====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRandomDomainConsistency(CPSolver cp) {
        Random rand = new Random(42);
        for (int trial = 0; trial < 30; trial++) {
            CPSolver cpLocal = CPFactory.makeSolver();
            int n = 3 + rand.nextInt(3); // 3 to 5
            int nVals = 2 + rand.nextInt(3); // 2 to 4

            @SuppressWarnings("unchecked")
            Set<Integer>[] domains = new Set[n];
            for (int i = 0; i < n; i++) {
                Set<Integer> dom = new HashSet<>();
                for (int v = 0; v < nVals; v++) {
                    if (rand.nextBoolean() || dom.isEmpty()) dom.add(v);
                }
                domains[i] = dom;
            }

            int[] low = new int[nVals];
            int[] up = new int[nVals];
            for (int v = 0; v < nVals; v++) {
                low[v] = rand.nextInt(2);
                up[v] = low[v] + 1 + rand.nextInt(2);
            }

            int maxViol = rand.nextInt(3);
            int expected = countSolutionsBruteForce(domains, 0, low, up, 0, maxViol);

            CPIntVar[] x = CPFactory.makeIntVarArray(domains.length, i -> CPFactory.makeIntVar(cpLocal, domains[i]));
            CPIntVar viol = CPFactory.makeIntVar(cpLocal, 0, maxViol);

            boolean failed = false;
            try {
                cpLocal.post(new SoftCardinalityDC(x, 0, low, up, viol));
            } catch (InconsistencyException e) {
                failed = true;
            }

            if (failed) {
                assertEquals(0, expected, "Trial " + trial + ": constraint failed but brute force found solutions");
                continue;
            }

            DFSearch search = CPFactory.makeDfs(cpLocal, Searches.firstFailBinary(x));
            SearchStatistics stats = search.solve();

            assertEquals(expected, stats.numberOfSolutions(),
                    "Trial " + trial + ": n=" + n + " nVals=" + nVals + " maxViol=" + maxViol);
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRandomNoSolutionRemoved(CPSolver cp) {
        // Verify constraint never removes valid solutions (viol unconstrained)
        Random rand = new Random(123);
        for (int trial = 0; trial < 20; trial++) {
            CPSolver cpLocal = CPFactory.makeSolver();
            int n = 3 + rand.nextInt(2);
            int nVals = 2 + rand.nextInt(2);

            @SuppressWarnings("unchecked")
            Set<Integer>[] domains = new Set[n];
            for (int i = 0; i < n; i++) {
                Set<Integer> dom = new HashSet<>();
                for (int v = 0; v < nVals; v++) {
                    if (rand.nextBoolean() || dom.isEmpty()) dom.add(v);
                }
                domains[i] = dom;
            }

            int[] low = new int[nVals];
            int[] up = new int[nVals];
            for (int v = 0; v < nVals; v++) {
                low[v] = rand.nextInt(2);
                up[v] = low[v] + rand.nextInt(3) + 1;
            }

            // large viol bound => should never remove solutions
            int maxViol = n * nVals;
            int expected = countSolutionsBruteForce(domains, 0, low, up, 0, maxViol);

            CPIntVar[] x = CPFactory.makeIntVarArray(domains.length, i -> CPFactory.makeIntVar(cpLocal, domains[i]));
            CPIntVar viol = CPFactory.makeIntVar(cpLocal, 0, maxViol);

            try {
                cpLocal.post(new SoftCardinalityDC(x, 0, low, up, viol));
            } catch (InconsistencyException e) {
                assertEquals(0, expected, "Trial " + trial);
                continue;
            }

            DFSearch search = CPFactory.makeDfs(cpLocal, Searches.firstFailBinary(x));
            SearchStatistics stats = search.solve();

            assertEquals(expected, stats.numberOfSolutions(), "Trial " + trial + ": solution removed!");
        }
    }
}
