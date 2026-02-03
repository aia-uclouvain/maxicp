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

class NonOverlapBCTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOK(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        CPIntervalVar[] vars = new CPIntervalVar[]{interval1, interval2};

        interval1.setLength(1);
        interval2.setLength(1);
        interval1.setEndMax(2);
        interval2.setEndMax(2);

        cp.post(new NoOverlapBC(vars));

        DFSearch dfs = CPFactory.makeDfs(cp, and(branchOnStatus(vars), branchOnPresentStarts(vars)));

        // solutions 7:
        // -,- both absent
        // _,0
        // _,1
        // 0,_
        // 1,_
        // 0,1 both present
        // 1,0 both present

        SearchStatistics stats = dfs.solve();

        assertEquals(7, stats.numberOfSolutions());
    }

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
            cp.post(new NoOverlapBC(intervals));
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
        cp.post(new NoOverlapBC(intervals));

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
        cp.post(new NoOverlapBC(intervals));

        DFSearch dfs = CPFactory.makeDfs(cp, branchOnPresentStarts(intervals));

        SearchStatistics stats = dfs.solve();

        assertEquals(120, stats.numberOfSolutions(), "disjunctive alldiff expect makeIntVarArray permutations");
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTimeoutBasicFiltering(CPSolver cp) {
        CPIntervalVar[] intervals = new CPIntervalVar[] {
                makeIntervalVar(cp, 1),
                makeIntervalVar(cp, 1),
                makeIntervalVar(cp, 1),
                makeIntervalVar(cp, 1),
        };
        intervals[0].setPresent();
        intervals[0].setStartMin(1);
        intervals[0].setStartMax(3); // present, [1..3] -> [2..4]
        intervals[1].setStart(2); // present, [2] -> [3]
        intervals[2].setAbsent();
        intervals[2].setStart(1); // absent, [1] -> [2]
        intervals[3].setStartMin(2);
        intervals[3].setStartMax(3); // present, [2..3] -> [3..4]
        assertDoesNotThrow(() -> cp.post(nonOverlap(intervals)));
    }


    /**
     * Tests the constraint with intervals that are optional
     * Some or all intervals may be absent in a solution
     * @param cp solver to use
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoOVerlapWithOptional(CPSolver cp) {
        int n = 4;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp);
            intervals[i].setLength(1);
            intervals[i].setEndMax(n);
        }
        cp.post(new NoOverlapBC(intervals));

        // branching that takes an interval unfixed and decides to either
        // - fix it to present if it was not decided
        // - fix it start if it was not decided
        // any of those two types of decision may happen: not all variables need to be fixed before fixing their start
        Random random = new Random(42);
        DFSearch dfs = CPFactory.makeDfs(cp, () -> {
            CPIntervalVar randomIntervalVarOptional = selectMin(intervals,
                    CPIntervalVar::isOptional,
                    i -> random.nextInt(100));
            CPIntervalVar randomIntervalVarUnfixedStart = selectMin(intervals,
                    i -> i.startMax() != i.startMin(),
                    i -> random.nextInt(100));
            if (randomIntervalVarOptional == null && randomIntervalVarUnfixedStart == null) {
                return EMPTY;
            }
            // the two variables that may be selected
            CPIntervalVar[] toBranch = new CPIntervalVar[] {randomIntervalVarOptional, randomIntervalVarUnfixedStart};
            // take randomly one of the two variables, if they can be selected
            int lb = randomIntervalVarOptional == null ? 1 : 0;
            int ub = 2 - (randomIntervalVarUnfixedStart == null ? 1 : 0);
            int selectedId = random.nextInt(lb, ub);
            // only used for understandable error messages
            StringJoiner joiner = new StringJoiner("\n");
            joiner.add("error with variables:");
            for (int i = 0 ; i < intervals.length ; i++) {
                joiner.add("  interval[" + i + "]: " + intervals[i].toString());
            }
            if (selectedId == 0) { // branch on an optional interval
                assertTrue(randomIntervalVarOptional.isOptional());
                return branch(
                        // either fix to start
                        () -> assertTimeoutPreemptively(Duration.ofSeconds(1), () -> cp.post(CPFactory.present(randomIntervalVarOptional)),
                                joiner.add("and branching on setPresent(" + randomIntervalVarOptional + ")").toString()),
                        // or postpone to later
                        () -> assertTimeoutPreemptively(Duration.ofSeconds(1), () -> cp.post(CPFactory.absent(randomIntervalVarOptional)),
                                joiner.add("and branching on setAbsent(" + randomIntervalVarOptional + ")").toString()));
            } else { // branch on an interval with an unfixed start
                assertNotEquals(randomIntervalVarUnfixedStart.startMin(), randomIntervalVarUnfixedStart.startMax());
                int start = randomIntervalVarUnfixedStart.startMin();
                joiner.add("branching on start = " + start + " for " + randomIntervalVarOptional);
                return branch(
                        // either begin at the given start
                        () -> assertTimeoutPreemptively(Duration.ofSeconds(1), () -> cp.post(CPFactory.startAt(randomIntervalVarUnfixedStart, start)),
                                joiner.add("and branching on startAt(" + randomIntervalVarOptional + ", " + start + ")").toString()),
                        // or postpone to later
                        () -> assertTimeoutPreemptively(Duration.ofSeconds(1), () -> cp.post(CPFactory.startAfter(randomIntervalVarUnfixedStart, start + 1)),
                                joiner.add("and branching on startAtOrAfter(" + randomIntervalVarOptional + ", " + (start+1) + ")").toString()));
            }
        });

        // manually checks the solutions
        boolean[] present = new boolean[n];
        dfs.onSolution(() -> {
            // clear the array, it was used in a past call
            Arrays.fill(present, false);
            for (CPIntervalVar intervalVar: intervals) {
                if (intervalVar.isPresent()) {
                    int start = intervalVar.startMin();
                    assertFalse(present[start], "Two intervals are present at time " + start); // must be the only interval at this time
                    present[start] = true;
                }
            }
        });
        // does not count the number of solutions, simply runs the DFS
        dfs.solve(s -> s.numberOfNodes() >= 10000);
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
     * Tests that the solutions found with a no overlap are the same with and without decomposition.
     * Uses optional tasks.
     * @param duration durations of intervals to tests
     */
    @ParameterizedTest
    @MethodSource("getDurations")
    public void testSameSolutionsAsDecompositionOptionals(int[] duration) {
        int maxDuration = Arrays.stream(duration).max().getAsInt();
        int sumDuration = Arrays.stream(duration).sum();
        // allows to place all activities with a small slack between them
        int startMax = sumDuration - maxDuration + duration.length;
        CPSolver cp = makeSolver();
        CPIntervalVar[] intervals = new CPIntervalVar[duration.length];
        for (int i = 0 ; i < duration.length ; i++) {
            intervals[i] = makeIntervalVar(cp);
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
        cp.post(new NoOverlapBC(intervals));
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