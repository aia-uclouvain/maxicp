/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DivVar}: the constraint x / y = z (integer division, truncation towards zero).
 *
 * <p>The key correctness criterion is <em>no solution is lost</em>: every triple
 * (a, b, c) that satisfies {@code a / b == c} (with b ≠ 0) and belongs to the
 * initial Cartesian product of the three domains must appear in the enumerated
 * solution set.  Spurious solutions (tuples that do NOT satisfy the relation)
 * must never appear either.
 *
 * <p>All tests verify correctness by comparing the solver's enumerated solution
 * set against an independent brute-force reference set built directly from the
 * initial domains.
 */
class DivVarTest extends CPSolverTest {

    // -------------------------------------------------------------------------
    // Helper: collect initial domain values into a plain int[]
    // -------------------------------------------------------------------------

    private static int[] domainOf(CPIntVar v) {
        int[] vals = new int[v.size()];
        v.fillArray(vals);
        return vals;
    }

    /**
     * Brute-force reference: all (a, b, c) with a in domX, b in domY (b≠0),
     * c in domZ such that a / b == c  (Java truncation-towards-zero semantics).
     */
    private static Set<String> bruteForce(int[] domX, int[] domY, int[] domZ) {
        Set<Integer> setZ = IntStream.of(domZ).boxed().collect(Collectors.toSet());
        Set<String> expected = new HashSet<>();
        for (int a : domX) {
            for (int b : domY) {
                if (b == 0) continue;          // division by zero is undefined
                int c = a / b;                 // Java integer division: truncates toward zero
                if (setZ.contains(c)) {
                    expected.add(a + "," + b + "," + c);
                }
            }
        }
        return expected;
    }

    /**
     * Core helper: creates the three variables with the given domains, posts
     * DivVar, enumerates all solutions, and asserts that the enumerated set
     * equals the brute-force reference set.
     */
    private static void checkCorrectness(CPSolver cp,
                                         int[] domX, int[] domY, int[] domZ) {
        CPIntVar x = CPFactory.makeIntVar(cp, IntStream.of(domX).boxed().collect(Collectors.toSet()));
        CPIntVar y = CPFactory.makeIntVar(cp, IntStream.of(domY).boxed().collect(Collectors.toSet()));
        CPIntVar z = CPFactory.makeIntVar(cp, IntStream.of(domZ).boxed().collect(Collectors.toSet()));

        // Snapshot initial domains BEFORE posting (post may reduce them)
        int[] initX = domainOf(x);
        int[] initY = domainOf(y);
        int[] initZ = domainOf(z);

        try {
            cp.post(new DivVar(x, y, z));
        } catch (InconsistencyException e) {
            // If the constraint detects unsatisfiability at post time,
            // the expected set must also be empty.
            Set<String> expected = bruteForce(initX, initY, initZ);
            assertTrue(expected.isEmpty(),
                    "Constraint reported inconsistency but brute-force found solutions: " + expected);
            return;
        }

        Set<String> found = new HashSet<>();
        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFailBinary(x, y, z));
        search.onSolution(() -> {
            assertTrue(x.isFixed() && y.isFixed() && z.isFixed());
            int a = x.min(), b = y.min(), c = z.min();
            assertNotEquals(0, b, "y=0 should never be in a solution of x/y=z");
            assertEquals(a / b, c,
                    "Solution (" + a + "/" + b + "=" + c + ") violates x/y=z");
            found.add(a + "," + b + "," + c);
        });

        SearchStatistics stats = search.solve();
        assertEquals(found.size(), stats.numberOfSolutions(),
                "Mismatch between onSolution count and stats");

        Set<String> expected = bruteForce(initX, initY, initZ);

        // Check no solution is lost
        Set<String> lost = new HashSet<>(expected);
        lost.removeAll(found);
        assertTrue(lost.isEmpty(), "Solutions lost by the constraint: " + lost);

        // Check no spurious solution was returned
        Set<String> spurious = new HashSet<>(found);
        spurious.removeAll(expected);
        assertTrue(spurious.isEmpty(), "Spurious solutions returned by the constraint: " + spurious);
    }

    // =========================================================================
    // Tests
    // =========================================================================

    /** Basic positive domains, exact results (6/2=3, 4/2=2). */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testPositiveDomains(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{2, 4, 6, 8, 10},
                new int[]{1, 2, 3},
                new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
    }

    /** Negative numerator, positive denominator. */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testNegativeNumerator(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{-9, -6, -4, -2, -1},
                new int[]{1, 2, 3},
                new int[]{-10, -9, -8, -7, -6, -5, -4, -3, -2, -1, 0});
    }

    /** Negative denominator, positive numerator. */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testNegativeDenominator(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{1, 2, 3, 4, 6, 8, 9},
                new int[]{-3, -2, -1},
                new int[]{-10, -9, -8, -7, -6, -5, -4, -3, -2, -1, 0});
    }

    /** Both x and y can be negative. */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testBothNegative(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{-9, -6, -4, -2, -1, 1, 2, 4, 6, 9},
                new int[]{-3, -2, -1, 1, 2, 3},
                new int[]{-6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6});
    }

    /**
     * y domain contains zero.  Zero must be excluded from y's solutions,
     * but all valid (b≠0) solutions must still be found.
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testDenominatorDomainContainsZero(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{-6, -4, -2, 0, 2, 4, 6},
                new int[]{-2, -1, 0, 1, 2},       // 0 is in initial domain of y
                new int[]{-6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6});
    }

    /**
     * x domain contains zero.  0/b=0 for all b≠0.
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testNumeratorContainsZero(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{-4, -2, 0, 2, 4},
                new int[]{-2, -1, 1, 2},
                new int[]{-4, -3, -2, -1, 0, 1, 2, 3, 4});
    }

    /**
     * z is fixed to 0 — tests truncation: a/b=0 iff |a| < |b|.
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testResultFixedToZero(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5},
                new int[]{-3, -2, -1, 1, 2, 3},
                new int[]{0});
    }

    /**
     * z is fixed to a positive constant.
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testResultFixedToPositive(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
                new int[]{1, 2, 3, 4, 5},
                new int[]{2});
    }

    /**
     * z is fixed to a negative constant.
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testResultFixedToNegative(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{-10, -9, -8, -7, -6, -5, -4, -3, -2, -1},
                new int[]{1, 2, 3, 4, 5},
                new int[]{-2});
    }

    /**
     * x is fixed (unit domain).
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testNumeratorFixed(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{7},
                new int[]{-4, -3, -2, -1, 1, 2, 3, 4},
                new int[]{-7, -6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7});
    }

    /**
     * y is fixed (unit domain, positive).
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testDenominatorFixedPositive(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{-10, -9, -8, -7, -6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
                new int[]{3},
                new int[]{-4, -3, -2, -1, 0, 1, 2, 3, 4});
    }

    /**
     * y is fixed (unit domain, negative).
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testDenominatorFixedNegative(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{-10, -9, -8, -7, -6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
                new int[]{-3},
                new int[]{-4, -3, -2, -1, 0, 1, 2, 3, 4});
    }

    /**
     * y's domain is {0} only — the constraint must detect global inconsistency
     * immediately (no b≠0 value exists).
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testDenominatorOnlyZero(CPSolver cp) {
        // brute-force will produce an empty set; checkCorrectness handles this
        checkCorrectness(cp,
                new int[]{1, 2, 3},
                new int[]{0},
                new int[]{0, 1, 2, 3});
    }

    /**
     * All three variables range over [-5..5].  Full mixed-sign stress test.
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testFullMixedSign(CPSolver cp) {
        int[] dom = IntStream.rangeClosed(-5, 5).toArray();
        checkCorrectness(cp, dom, dom, dom);
    }

    /**
     * Larger domains: x in [-20..20], y in [-5..5], z in [-10..10].
     * Exercises pruning quality and ensures completeness on bigger instances.
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testLargerDomains(CPSolver cp) {
        checkCorrectness(cp,
                IntStream.rangeClosed(-20, 20).toArray(),
                IntStream.rangeClosed(-5, 5).toArray(),
                IntStream.rangeClosed(-10, 10).toArray());
    }

    /**
     * Regression: x/y=z where the z domain is tighter than the quotient range —
     * some (a,b) pairs will have a/b outside z's domain and should be pruned.
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testResultDomainRestrictsXY(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12},
                new int[]{1, 2, 3, 4},
                new int[]{3});   // only pairs where a/b == 3
    }

    /**
     * Sparse, non-contiguous domains.
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    void testSparseDomains(CPSolver cp) {
        checkCorrectness(cp,
                new int[]{-10, -7, -3, 0, 3, 7, 10},
                new int[]{-3, -1, 1, 3},
                new int[]{-4, -3, -2, -1, 0, 1, 2, 3, 4});
    }
}

