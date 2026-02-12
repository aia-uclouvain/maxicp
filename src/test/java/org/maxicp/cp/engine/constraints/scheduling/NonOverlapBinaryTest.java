/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;

import static org.maxicp.cp.CPFactory.*;

import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.ArrayList;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.search.Searches.*;

class NonOverlapBinaryTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test0(CPSolver cp) {
        int n = 2;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        CPIntVar[] ends = CPFactory.makeIntVarArray(cp, n, 0, n);
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp);
            intervals[i].setPresent();
            intervals[i].setLength(5);
        }

        NoOverlapBinary binary = new NoOverlapBinary(intervals[0], intervals[1]);
        cp.post(binary);
        CPIntVar before = binary.before;

        cp.post(CPFactory.eq(before, 0)); // second activity before the first one

        assertEquals(5, intervals[0].startMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {
        int n = 2;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        CPIntVar[] ends = CPFactory.makeIntVarArray(cp, n, 0, n);
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp);
            intervals[i].setPresent();
            intervals[i].setLength(1);
            intervals[i].setEndMax(n);
            cp.post(new IntervalVarEnd(intervals[i], ends[i]));
        }
        cp.post(new NoOverlapBinary(intervals[0], intervals[1]));

        DFSearch dfs = CPFactory.makeDfs(cp, branchOnPresentStarts(intervals));

        SearchStatistics stats = dfs.solve();

        assertEquals(2, stats.numberOfSolutions());
    }

    // /!\ Slow
    @Disabled
    @ParameterizedTest
    @MethodSource("getSolver")
    public void test2(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp);
            intervals[i].setPresent();
            intervals[i].setLength(i * 5);
        }

        CPIntVar makespan = makespan(intervals);

        ArrayList<CPBoolVar> precedences = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                NoOverlapBinary nonOverlap = new NoOverlapBinary(intervals[i], intervals[j]);
                cp.post(nonOverlap);
                precedences.add(nonOverlap.before);
            }
        }

        Supplier<Runnable[]> fixMakespan = () -> makespan.isFixed() ? EMPTY : new Runnable[]{() -> {
            cp.post(CPFactory.eq(makespan, makespan.min()));
        }};

        DFSearch dfs = CPFactory.makeDfs(cp, and(firstFailBinary(precedences.toArray(new CPBoolVar[0])), fixMakespan));

        SearchStatistics stats = dfs.solve();

        assertEquals(6, stats.numberOfSolutions());
    }
}