/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.ArrayList;

import static org.maxicp.cp.CPFactory.*;
import static org.junit.jupiter.api.Assertions.*;

class NoOverlapBinaryWithTransitionTimeTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {

        CPIntervalVar interval1 = makeIntervalVar(cp, false, 5, 5);
        CPIntervalVar interval2 = makeIntervalVar(cp, false, 5, 5);

        CPBoolVar before = makeBoolVar(cp);

        cp.post(new NoOverlapBinaryWithTransitionTime(before, interval1, interval2, 2, 2));

        cp.post(eq(before, 1));

        assertEquals(7, interval2.startMin());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test2(CPSolver cp) {

        CPIntervalVar interval1 = makeIntervalVar(cp, false, 5, 5);
        CPIntervalVar interval2 = makeIntervalVar(cp, false, 5, 5);

        CPBoolVar before = makeBoolVar(cp);

        cp.post(new NoOverlapBinaryWithTransitionTime(before, interval1, interval2, 2, 2));

        cp.post(eq(before,0));

        assertEquals(7, interval1.startMin());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test3(CPSolver cp) {

        CPIntervalVar interval1 = makeIntervalVar(cp, false, 5, 5);
        CPIntervalVar interval2 = makeIntervalVar(cp, false, 5, 5);

        interval1.setEndMax(10);

        CPBoolVar before = makeBoolVar(cp);

        cp.post(new NoOverlapBinaryWithTransitionTime(before, interval1, interval2, 2, 2));

        assertTrue(before.isTrue());

    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void test4(CPSolver cp) {
        int n = 5;
        CPIntervalVar [] intervals = makeIntervalVarArray(cp, n);
        for (int i = 0; i < n; i++) {
            intervals[i].setLength(2);
            intervals[i].setPresent();
            intervals[i].setEndMax(18);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i+1; j < n; j++) {
                cp.post(new NoOverlapBinaryWithTransitionTime(intervals[i], intervals[j], 2, 2));
            }
        }
        DFSearch dfSearch = makeDfs(cp, Searches.setTimes(intervals));
        dfSearch.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                for (int j = i+1; j < n; j++) {
                    int starti = intervals[i].startMin();
                    int endi = intervals[i].endMin();
                    int startj = intervals[j].startMin();
                    int endj = intervals[j].endMin();
                    assertTrue(endi + 2 <= startj || endj + 2 <= starti);
                }
            }
        });
        SearchStatistics stats = dfSearch.solve();
        assertEquals(120,stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test5(CPSolver cp) {
        int n = 5;
        CPIntervalVar [] intervals = makeIntervalVarArray(cp, n);
        for (int i = 0; i < n; i++) {
            intervals[i].setLength(2);
            intervals[i].setPresent();
            intervals[i].setEndMax(18);
        }
        ArrayList<CPBoolVar> precedences = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i+1; j < n; j++) {
                CPBoolVar before = makeBoolVar(cp);
                cp.post(new NoOverlapBinaryWithTransitionTime(before,intervals[i], intervals[j], 2, 2));
                precedences.add(before);
            }
        }
        DFSearch dfSearch = makeDfs(cp, Searches.firstFailBinary(precedences.toArray(new CPBoolVar[0])));
        dfSearch.onSolution(() -> {
            for (int i = 0; i < n; i++) {
                for (int j = i+1; j < n; j++) {
                    int starti = intervals[i].startMin();
                    int endi = intervals[i].endMin();
                    int startj = intervals[j].startMin();
                    int endj = intervals[j].endMin();
                    assertTrue(endi + 2 <= startj || endj + 2 <= starti);
                }
            }
        });
        SearchStatistics stats = dfSearch.solve();
        assertEquals(120,stats.numberOfSolutions());
    }



}