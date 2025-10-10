/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.StringJoiner;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

class HeadTailConstraintTest extends CPSolverTest {


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBug(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp);
            intervals[i].setPresent();
            intervals[i].setLength(1);
            intervals[i].setEndMax(n);
        }

        try {
            cp.post(new HeadTailConstraint(intervals));
            intervals[0].setStart(2);
            cp.fixPoint();

            assertEquals(2, intervals[1].endMax());
            assertEquals(2, intervals[2].endMax());

            intervals[1].setStart(1);
            cp.fixPoint();

            assertEquals(3, intervals[0].endMax());

            intervals[2].setStart(0);
            cp.fixPoint();


        } catch (InconsistencyException e) {
            fail();
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAllDiffDisjunctiveSmall(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp);
            intervals[i].setPresent();
            intervals[i].setLength(1);
            intervals[i].setEndMax(n);
        }
        cp.post(new HeadTailConstraint(intervals));

        DFSearch dfs = CPFactory.makeDfs(cp, branchOnPresentStarts(intervals));

        SearchStatistics stats = dfs.solve();

        assertEquals(6, stats.numberOfSolutions(), "disjunctive alldiff expect makeIntVarArray permutations");
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAllDiffDisjunctive(CPSolver cp) {
        int n = 5;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp);
            intervals[i].setPresent();
            intervals[i].setLength(1);
            intervals[i].setEndMax(n);
        }
        cp.post(new HeadTailConstraint(intervals));

        DFSearch dfs = CPFactory.makeDfs(cp, branchOnPresentStarts(intervals));

        SearchStatistics stats = dfs.solve();

        assertEquals(120, stats.numberOfSolutions(), "disjunctive alldiff expect makeIntVarArray permutations");
    }

    /**
     * Gives arrays of durations, for test purposes
     */
    public static Stream<int[]> getDurations() {
        return Arrays.stream(new int[][] {
                {5, 4, 6, 7},
                {1, 2, 3},
                {1, 1, 1, 1},
                {10, 20, 15},
        });
    }

    /**
     * Tests that the solutions found with a no overlap are the same with and without decomposition.
     * Uses present tasks.
     * @param duration durations of intervals to tests
     */
    @ParameterizedTest
    @MethodSource("getDurations")
    public void testSameSolutionsAsDecomposition(int[] duration) {
        int maxDuration = Arrays.stream(duration).max().getAsInt();
        int sumDuration = Arrays.stream(duration).sum();
        // allows to place all activities with a small slack between them
        int startMax = sumDuration - maxDuration + duration.length;
        CPSolver cp = makeSolver();
        CPIntervalVar[] intervals = new CPIntervalVar[duration.length];
        for (int i = 0 ; i < duration.length ; i++) {
            intervals[i] = makeIntervalVar(cp);
            intervals[i].setPresent();
            intervals[i].setLength(duration[i]);
            intervals[i].setStartMax(startMax);
        }
        assertSameSolutionDecomposition(intervals);
    }

    /**
     * Asserts that the number of solutions found when using a no overlap are the same with and without a decomposition
     * @param intervals intervals over which the assertion must be performed
     */
    public static void assertSameSolutionDecomposition(CPIntervalVar[] intervals) {
        CPSolver cp = intervals[0].getSolver();
        cp.fixPoint();
        cp.getStateManager().saveState();
        cp.getStateManager().saveState();
        cp.post(new HeadTailConstraint(intervals));
        SearchStatistics statsNoOverlap = makeDfs(cp, and(Searches.branchOnStatus(intervals), Searches.branchOnPresentStarts(intervals))).solve();
        cp.getStateManager().restoreState();
        postDecomposition(intervals);
        SearchStatistics statsDecomposition = makeDfs(cp, and(Searches.branchOnStatus(intervals), Searches.branchOnPresentStarts(intervals))).solve();
        assertEquals(statsNoOverlap.numberOfSolutions(), statsDecomposition.numberOfSolutions());
        cp.getStateManager().restoreState();
    }

    /**
     * Post a no overlap through a decomposition with O(n^2) constraints
     * @param intervals intervals over which the no overlap decomposition must be applied
     */
    public static void postDecomposition(CPIntervalVar[] intervals) {
        CPSolver cp = intervals[0].getSolver();
        for (int i = 0; i < intervals.length; i++) {
            for (int j = i + 1; j < intervals.length; j++) {
                // i before j or j before i:
                CPBoolVar iBeforej = makeBoolVar(cp);
                CPBoolVar jBeforei = not(iBeforej);

                cp.post(new IsEndBeforeStart(intervals[i], intervals[j], iBeforej));
                cp.post(new IsEndBeforeStart(intervals[j], intervals[i], jBeforei));
            }
        }
    }

}