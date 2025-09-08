/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.Constants;
import org.maxicp.cp.CPFactory;

import static org.maxicp.cp.CPFactory.*;

import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.maxicp.search.Searches.and;
import static org.maxicp.search.Searches.firstFail;

class CumulativeFunctionConstraintTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSimpleAllDiffWithCumulative(CPSolver cp) {
        int n = 5; // number of intervals
        int l = 5; // length of intervals
        int h = 8; // height of each interval
        int C = 10; // capacity of the resource

        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, false, l);
            intervals[i].setEndMax(l * n);
        }

        CPCumulFunction profile = new CPFlatCumulFunction();
        for (int i = 0; i < n; i++) {
            profile = CPFactory.plus(profile, new CPPulseCumulFunction(intervals[i], h, h));
        }

        cp.post(le(profile, 10));

        CPIntVar[] starts = CPFactory.makeIntVarArray(n, i -> startOr(intervals[i], 0));

        DFSearch dfs = new DFSearch(cp.getStateManager(), firstFail(starts));

        SearchStatistics stats = dfs.solve();

        assertEquals(120, stats.numberOfSolutions());
    }

    // /!\ Slow
    @Disabled
    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSimpleAllDiffWithCumulativeAndOptional(CPSolver cp) {
        int n = 4; // number of intervals
        int l = 5; // length of intervals
        int h = 8; // height of each interval
        int C = 10; // capacity of the resource

        CPIntervalVar[] intervals = new CPIntervalVar[n];
        CPIntVar[] present = new CPIntVar[n];

        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, true, l);
            intervals[i].setEndMax(l * (n - 1)); // one activity will be excluded
            present[i] = intervals[i].status();
        }

        // at least n-1 activities are present
        cp.post(CPFactory.ge(CPFactory.sum(present), n - 1));

        CPCumulFunction profile = new CPFlatCumulFunction();
        for (int i = 0; i < n; i++) {
            profile = CPFactory.plus(profile, new CPPulseCumulFunction(intervals[i], h, h));
        }

        cp.post(le(profile, C));

        CPIntVar[] starts = CPFactory.makeIntVarArray(n, i -> startOr(intervals[i], 0));

        DFSearch dfs = new DFSearch(cp.getStateManager(), and(firstFail(starts), firstFail(present)));

        SearchStatistics stats = dfs.solve();

        // 4 (for the 4 possibilities to have an optional) * 3! (all permutations of remaining ones)
        assertEquals(24, stats.numberOfSolutions());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSimpleAllDiffWithCumulativeVariableHeight(CPSolver cp) {
        int n = 4; // number of intervals
        int l = 4; // length of intervals
        int hmin = 8;  // height min of each interval
        int hmax = 12; // height max of each interval
        int C = 10; // capacity of the resource

        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, false, l);
            intervals[i].setEndMax(l * n);
        }

        CPCumulFunction profile = new CPFlatCumulFunction();
        for (int i = 0; i < n; i++) {
            profile = CPFactory.plus(profile, new CPPulseCumulFunction(intervals[i], hmin, hmax));
        }

        cp.post(le(profile, C));

        CPIntVar[] starts = CPFactory.makeIntVarArray(n, i -> startOr(intervals[i], 0));

        final CPCumulFunction cumProfile = profile;

        CPIntVar[] heightAtStarts = CPFactory.makeIntVarArray(n, i -> cumProfile.heightAtStart(intervals[i]));

        DFSearch dfs = new DFSearch(cp.getStateManager(), and(firstFail(heightAtStarts), firstFail(starts)));

        // (3 ^ 4) * 4! = 1944 solutions (since the height 8,9 and 10 are possible

        SearchStatistics stats = dfs.solve();

        assertEquals(1944, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSimpleAllDiffWithProducers(CPSolver cp) {
        int n = 4; // number of intervals
        int l = 4; // length of intervals
        int hConsumer = 15;  // height min of each interval
        int hProducer = 8; // height max of each interval
        int cGlobal = 10; // capacity of the resource with both producers and consumers
        int cConsumer = 20; // capacity of the resource with only consumers (to enforce alldiff)

        CPIntervalVar[] intervals = new CPIntervalVar[2 * n];
        for (int i = 0; i < 2 * n; i++) {
            intervals[i] = makeIntervalVar(cp, false, l);
            intervals[i].setEndMax(l * n);
        }

        CPCumulFunction profileAll = new CPFlatCumulFunction();
        CPCumulFunction profileConsumer = new CPFlatCumulFunction();

        // consumer
        for (int i = 0; i < n; i++) {
            profileAll = CPFactory.plus(profileAll, new CPPulseCumulFunction(intervals[i], hConsumer, hConsumer));
            profileConsumer = CPFactory.plus(profileConsumer, pulse(intervals[i], hConsumer));
        }

        cp.post(le(profileConsumer, cConsumer));

        // producer
        for (int i = n; i < 2 * n; i++) {
            profileAll = minus(profileAll, new CPPulseCumulFunction(intervals[i], hProducer, hProducer));
        }

        cp.post(le(profileAll, cGlobal));

        CPIntVar[] starts = CPFactory.makeIntVarArray(2 * n, i -> startOr(intervals[i], 0));

        final CPCumulFunction cumProfile = profileAll;

        DFSearch dfs = new DFSearch(cp.getStateManager(), firstFail(starts));

        // 4! * 4! = 576 solutions (permutations of producers * permutations of consumers)

        SearchStatistics stats = dfs.solve();

        assertEquals(576, stats.numberOfSolutions());
    }

    // /!\ Slow
    @Disabled
    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSimpleAllDiffWithProducersVariableHeight(CPSolver cp) {
        int n = 4; // number of intervals
        int l = 4; // length of intervals
        int C = 3; // capacity of the resource

        CPIntervalVar[] intervals = new CPIntervalVar[2 * n];
        for (int i = 0; i < 2 * n; i++) {
            intervals[i] = makeIntervalVar(cp, false, l);
            intervals[i].setEndMax(l * n);
        }

        CPCumulFunction profile = new CPFlatCumulFunction();

        // consumer
        for (int i = 0; i < n; i++) {
            profile = CPFactory.plus(profile, pulse(intervals[i], 5));
        }

        cp.post(le(profile, 5));

        // producer
        for (int i = n; i < 2 * n; i++) {
            profile = minus(profile, pulse(intervals[i], 1, 3));
        }

        final CPCumulFunction profile_ = profile;

        CPIntVar[] heights = CPFactory.makeIntVarArray(2 * n, i -> profile_.heightAtStart(intervals[i]));

        cp.post(le(profile, C));

        CPIntVar[] starts = CPFactory.makeIntVarArray(2 * n, i -> startOr(intervals[i], 0));


        DFSearch dfs = new DFSearch(cp.getStateManager(), and(firstFail(heights), firstFail(starts)));


        dfs.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                for (int j = n; j < 2 * n; j++) {
                    if (starts[i].min() == starts[j].min()) {
                        assertEquals(5, heights[i].min());
                        assertTrue(heights[i].min() + heights[j].min() <= 3);
                    }
                }
            }
        });

        // 4! * 4! * (2^4) = 9216 solutions (permutations of producers * permutations of consumers * all possible producers height)

        SearchStatistics stats = dfs.solve();

        assertEquals(9216, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAlwaysInCumulative(CPSolver cp) {
        int n = 5; // number of intervals
        int l = 1; // length of intervals
        int h = 1; // height of each interval
        int Cmin = 1; // capacity min of the resource
        int Cmax = 2; // capacity max of the resource

        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, false, l);
            intervals[i].setEndMax(3);
        }

        CPCumulFunction profile = new CPFlatCumulFunction();
        for (int i = 0; i < n; i++) {
            profile = CPFactory.plus(profile, new CPPulseCumulFunction(intervals[i], h, h));
        }

        cp.post(alwaysIn(profile, Cmin, Cmax));

        CPIntVar[] starts = CPFactory.makeIntVarArray(n, i -> startOr(intervals[i], 0));

        DFSearch dfs = new DFSearch(cp.getStateManager(), firstFail(starts));

        SearchStatistics stats = dfs.solve();

        assertEquals(90, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAlwaysInCumulativeRange(CPSolver cp) {
        int n = 5; // number of intervals
        int l = 1; // length of intervals
        int h = 1; // height of each interval
        int Cmin = 1; // capacity min of the resource
        int Cmax = 2; // capacity max of the resource

        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, false, l);
            intervals[i].setEndMax(5);
        }

        CPCumulFunction profile = new CPFlatCumulFunction();
        for (int i = 0; i < n; i++) {
            profile = CPFactory.plus(profile, new CPPulseCumulFunction(intervals[i], h, h));
        }

        cp.post(alwaysIn(profile, Cmin, Cmax, 1, 5));

        CPIntVar[] starts = CPFactory.makeIntVarArray(n, i -> startOr(intervals[i], 0));

        DFSearch dfs = new DFSearch(cp.getStateManager(), firstFail(starts));

        SearchStatistics stats = dfs.solve();

        assertEquals(360, stats.numberOfSolutions());
    }



    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOptimalFilteringHeight(CPSolver cp) {
        int n = 4; // number of intervals

        CPIntervalVar[] intervals = new CPIntervalVar[4];

        intervals[0] = makeIntervalVar(cp, false, 6);
        intervals[0].setStartMin(8);
        intervals[0].setEndMax(24);

        intervals[1] = makeIntervalVar(cp, false, 12);
        intervals[1].setStart(0);

        intervals[2] = makeIntervalVar(cp, false, 12);
        intervals[2].setStart(20);

        intervals[3] = makeIntervalVar(cp, false, 4);
        intervals[3].setStart(14);

        CPCumulFunction profile = new CPFlatCumulFunction();
        profile = CPFactory.plus(profile, new CPPulseCumulFunction(intervals[0], 3, 6));
        profile = CPFactory.minus(profile, new CPPulseCumulFunction(intervals[1], 4, 4));
        profile = CPFactory.minus(profile, new CPPulseCumulFunction(intervals[2], 4, 4));
        profile = CPFactory.minus(profile, new CPPulseCumulFunction(intervals[3], 1, 1));

        CPIntVar h0 = profile.heightAtStart(intervals[0]);

        cp.post(le(profile, 4));

        assertEquals(5, h0.max());
    }
}