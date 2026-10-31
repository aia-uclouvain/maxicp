/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.ModelDispatcher;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.constraints.scheduling.NoOverlap;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.SymbolicModel;
import org.maxicp.search.blackbox.BlackBoxSearch;
import org.maxicp.search.blackbox.SchedulingLNSRunnableSearch;
import org.maxicp.search.blackbox.SchedulingRunnableSearch;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;

public class SearchStrategiesAndSchedulingLNSTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRawSetTimesSearchAndDominance(CPSolver cp) {
        int n = 3;
        CPIntervalVar[] tasks = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            tasks[i] = makeIntervalVar(cp, false, 2);
            tasks[i].setEndMax(7);
        }
        cp.post(new NoOverlap(tasks));

        CPIntVar makespan = max(
                endOr(tasks[0], 0),
                endOr(tasks[1], 0),
                endOr(tasks[2], 0)
        );
        Objective obj = cp.minimize(makespan);

        DFSearch dfs = makeDfs(cp, SetTimes.setTimes(tasks, i -> i));
        int[] bestMakespan = new int[]{Integer.MAX_VALUE};
        dfs.onSolution(() -> bestMakespan[0] = makespan.min());
        SearchStatistics stats = dfs.optimize(obj);
        assertTrue(stats.numberOfSolutions() >= 1);
        assertEquals(6, bestMakespan[0]);

        // Also test default SetTimes(tasks) constructor with single-branch case (minEct >= lct) and tie-breaking
        CPSolver cp2 = makeSolver();
        CPIntervalVar t0 = makeIntervalVar(cp2, false, 3);
        t0.setStartMin(0);
        t0.setEndMax(4); // startMax = 1 < minEct = 3 -> single branch!
        CPIntervalVar t1 = makeIntervalVar(cp2, false, 3);
        t1.setStartMin(0);
        t1.setEndMax(7);
        CPIntervalVar[] tasks2 = new CPIntervalVar[]{t0, t1};
        cp2.post(new NoOverlap(tasks2));

        DFSearch dfs2 = makeDfs(cp2, Searches.setTimes(tasks2));
        SearchStatistics stats2 = dfs2.solve();
        assertTrue(stats2.numberOfSolutions() >= 1);
    }

    @Test
    public void testRankModelingAndSearchesIntervalBranching() throws Exception {
        try (ModelDispatcher model = Factory.makeModelDispatcher()) {
            IntervalVar t0 = model.intervalVar(0, 6, 2, true);
            IntervalVar t1 = model.intervalVar(0, 6, 2, true);
            IntervalVar t2 = model.intervalVar(0, 6, 2, true);
            IntervalVar[] tasks = new IntervalVar[]{t0, t1, t2};
            model.add(Factory.noOverlap(tasks));

            model.runCP(cp -> {
                Supplier<Runnable[]> rank1 = RankModeling.rank(tasks);
                RankModeling rank2 = new RankModeling(new IntervalVar[][]{tasks});
                assertNotNull(rank1);
                assertNotNull(rank2);

                DFSearch dfs = cp.dfSearch(Searches.and(
                        rank2,
                        Searches.branchOnPresentStarts(tasks)
                ));
                SearchStatistics stats = dfs.solve();
                assertTrue(stats.numberOfSolutions() >= 6);
            });
        }

        // Test branchOnStartMin, setTimes(IntervalVar...), and raw branchOnStatus / branchOnStatusThenStarts
        try (ModelDispatcher model = Factory.makeModelDispatcher()) {
            IntervalVar opt0 = model.intervalVar(0, 5, 2, true);
            IntervalVar opt1 = model.intervalVar(0, 5, 2, true);
            IntervalVar[] opts = new IntervalVar[]{opt0, opt1};
            model.add(Factory.noOverlap(opts));

            model.runCP(cp -> {
                DFSearch dfs1 = cp.dfSearch(Searches.and(
                        () -> Searches.branchOnStartMin(opt0),
                        () -> Searches.branchOnStartMin(opt1)
                ));
                SearchStatistics s1 = dfs1.solve();
                assertEquals(6, s1.numberOfSolutions());

                DFSearch dfs2 = cp.dfSearch(Searches.and(
                        Searches.setTimes(opts),
                        Searches.branchOnPresentStarts(opts)
                ));
                SearchStatistics s2 = dfs2.solve();
                assertTrue(s2.numberOfSolutions() > 0);
            });
        }

        CPSolver rawCp = makeSolver();
        CPIntervalVar ro0 = makeIntervalVar(rawCp, true, 2);
        ro0.setEndMax(4);
        CPIntervalVar ro1 = makeIntervalVar(rawCp, true, 2);
        ro1.setEndMax(4);
        CPIntervalVar[] rOpts = new CPIntervalVar[]{ro0, ro1};
        rawCp.post(new NoOverlap(rOpts));
        assertEquals(9, makeDfs(rawCp, Searches.branchOnStatusThenStarts(rOpts)).solve().numberOfSolutions());
    }

    @Test
    public void testConcurrentDFSearchAndBestFirstSearch() throws Exception {
        try (ModelDispatcher model = Factory.makeModelDispatcher()) {
            IntVar[] x = model.intVarArray(4, 4);
            model.add(Factory.allDifferent(x));
            IntExpression sumObj = Factory.sum(
                    Factory.mul(x[0], 1),
                    Factory.mul(x[1], 2),
                    Factory.mul(x[2], 3),
                    Factory.mul(x[3], 4)
            );

            model.runCP(cp -> {
                // 1. ConcurrentDFSearch solve & optimize
                ConcurrentDFSearch cdfs = cp.concurrentDFSearch(() -> {
                    IntVar sel = Searches.selectMin(x, v -> v.size() > 1, IntVar::size);
                    if (sel == null) return new SymbolicModel[0];
                    int val = sel.min();
                    SymbolicModel cur = cp.symbolicCopy();
                    return Searches.branch(
                            cur.add(new org.maxicp.modeling.constraints.ExpressionIsTrue(Factory.eq(sel, val))),
                            cur.add(new org.maxicp.modeling.constraints.ExpressionIsTrue(Factory.neq(sel, val)))
                    );
                });
                AtomicInteger solCount = new AtomicInteger(0);
                AtomicInteger failCount = new AtomicInteger(0);
                cdfs.onSolution(solCount::incrementAndGet);
                cdfs.onFailure(failCount::incrementAndGet);

                SearchStatistics statsSolve = cdfs.solve();
                assertEquals(24, statsSolve.numberOfSolutions());
                assertEquals(24, solCount.get());
                assertTrue(failCount.get() >= 0);

                SearchStatistics statsLimit = cdfs.solve(s -> s.numberOfNodes() >= 2);
                assertFalse(statsLimit.isCompleted());

                Objective minObj = cp.minimize(sumObj);
                SearchStatistics statsOpt = cdfs.optimize(minObj);
                assertTrue(statsOpt.numberOfSolutions() >= 1);

                // 2. BestFirstSearch solve & optimize & getUnexploredModels
                BestFirstSearch<Integer> bfs = cp.bestFirstSearch(
                        Searches.firstFailBinary(x),
                        () -> x[0].min() + x[1].min()
                );
                AtomicInteger bfsSols = new AtomicInteger(0);
                bfs.onSolution(bfsSols::incrementAndGet);
                bfs.onFailure(() -> {});
                SearchStatistics bfsStats = bfs.solve();
                assertEquals(24, bfsStats.numberOfSolutions());
                assertEquals(24, bfsSols.get());

                SearchStatistics bfsLimit = bfs.solve(s -> s.numberOfNodes() >= 2);
                assertFalse(bfsLimit.isCompleted());
                SymbolicModel[] unexplored = bfs.getUnexploredModels();
                assertTrue(unexplored.length > 0);

                Objective minObj2 = cp.minimize(sumObj);
                SearchStatistics bfsOptStats = bfs.optimize(minObj2);
                assertTrue(bfsOptStats.numberOfSolutions() >= 1);

                Objective minObj3 = cp.minimize(sumObj);
                SearchStatistics bfsOptSubj = bfs.optimizeSubjectTo(
                        minObj3,
                        s -> s.numberOfSolutions() >= 1,
                        () -> model.add(Factory.eq(x[0], 3))
                );
                assertEquals(1, bfsOptSubj.numberOfSolutions());
            });
        }
    }

    @Test
    public void testSearchesCombinatorsAndHeuristics() throws Exception {
        try (ModelDispatcher model = Factory.makeModelDispatcher()) {
            IntVar[] x = model.intVarArray(4, 4);
            model.add(Factory.allDifferent(x));
            IntExpression objExpr = Factory.sum(
                    Factory.mul(x[0], 4),
                    Factory.mul(x[1], 3),
                    Factory.mul(x[2], 2),
                    Factory.mul(x[3], 1)
            );

            // Test selectMin overloads
            IntVar minVar3 = Searches.selectMin(x, v -> v.size() > 1, IntVar::size);
            assertSame(x[0], minVar3);
            IntVar minVar4 = Searches.selectMin(x, 2, v -> v.size() > 1, IntVar::size);
            assertSame(x[0], minVar4);
            int minIdx = Searches.selectMin(new int[]{0, 1, 2, 3}, 4, i -> x[i].size() > 1, i -> i).getAsInt();
            assertEquals(0, minIdx);

            model.runCP(cp -> {
                // boundImpactValueSelector with small domain (<= 100)
                DFSearch dfsBound = cp.dfSearch(Searches.heuristicBinary(
                        () -> Searches.selectMin(x, v -> !v.isFixed(), IntVar::size),
                        Searches.boundImpactValueSelector(objExpr)
                ));
                assertEquals(24, dfsBound.solve().numberOfSolutions());

                // boundImpactValueSelector with large domain (> 100)
                IntVar largeVar = model.intVar(0, 120);
                model.add(Factory.le(largeVar, 2));
                int chosenVal = Searches.boundImpactValueSelector(largeVar).apply(largeVar);
                assertTrue(largeVar.contains(chosenVal));

                // lastConflict
                DFSearch dfsLC1 = cp.dfSearch(Searches.lastConflict(
                        () -> Searches.selectMin(x, v -> !v.isFixed(), IntExpression::size),
                        IntExpression::min
                ));
                assertEquals(24, dfsLC1.solve().numberOfSolutions());

                // conflictOrderingSearch
                DFSearch dfsCOS = cp.dfSearch(Searches.conflictOrderingSearch(
                        () -> Searches.selectMin(x, v -> !v.isFixed(), IntExpression::size),
                        IntExpression::min
                ));
                assertEquals(24, dfsCOS.solve().numberOfSolutions());

                assertEquals(24, cp.dfSearch(Searches.conflictOrderingSearch(x)).solve().numberOfSolutions());

                // limitedDiscrepancy
                DFSearch dfsLDS = cp.dfSearch(Searches.limitedDiscrepancy(
                        Searches.firstFailBinary(x), 2
                ));
                SearchStatistics ldsStats = dfsLDS.solve();
                assertTrue(ldsStats.numberOfSolutions() > 0 && ldsStats.numberOfSolutions() < 24);

                // firstFailNary, staticOrderNary, staticOrderBinary
                assertEquals(24, cp.dfSearch(Searches.firstFailNary(x)).solve().numberOfSolutions());
                assertEquals(24, cp.dfSearch(Searches.staticOrderNary(x)).solve().numberOfSolutions());
                assertEquals(24, cp.dfSearch(Searches.staticOrderBinary(x)).solve().numberOfSolutions());

                // and combinators (2, 3, 4, 5, varargs)
                Supplier<Runnable[]> b0 = Searches.firstFailBinary(x[0]);
                Supplier<Runnable[]> b1 = Searches.firstFailBinary(x[1]);
                Supplier<Runnable[]> b2 = Searches.firstFailBinary(x[2]);
                Supplier<Runnable[]> b3 = Searches.firstFailBinary(x[3]);
                Supplier<Runnable[]> nop = () -> Searches.EMPTY;

                assertEquals(24, cp.dfSearch(Searches.and(b0, Searches.and(b1, b2, b3))).solve().numberOfSolutions());
                assertEquals(24, cp.dfSearch(Searches.and(b0, b1, b2, b3)).solve().numberOfSolutions());
                assertEquals(24, cp.dfSearch(Searches.and(b0, b1, b2, b3, nop)).solve().numberOfSolutions());
                @SuppressWarnings("unchecked")
                Supplier<Runnable[]>[] arr = new Supplier[]{b0, b1, b2, b3, nop, nop};
                assertEquals(24, cp.dfSearch(Searches.and(arr)).solve().numberOfSolutions());
            });
        }
    }

    @Test
    public void testSearchesFDSAndSeqVarSelectors() throws Exception {
        // 1. Modeling FDS overloads
        try (ModelDispatcher model = Factory.makeModelDispatcher()) {
            IntVar[] x = model.intVarArray(3, 3);
            model.add(Factory.allDifferent(x));
            IntervalVar t0 = model.intervalVar(0, 4, 2, true);
            IntervalVar t1 = model.intervalVar(0, 4, 2, true);
            IntervalVar[] tasks = new IntervalVar[]{t0, t1};
            model.add(Factory.noOverlap(tasks));

            model.runCP(cp -> {
                assertEquals(6, cp.dfSearch(Searches.fds(x)).solve().numberOfSolutions());
                assertEquals(6, cp.dfSearch(Searches.fds(new IntExpression[][]{x})).solve().numberOfSolutions());
                assertEquals(2, cp.dfSearch(Searches.fds(tasks)).solve().numberOfSolutions());
                assertEquals(2, cp.dfSearch(Searches.fds(new IntervalVar[][]{tasks})).solve().numberOfSolutions());
                assertEquals(12, cp.dfSearch(Searches.fds(tasks, x)).solve().numberOfSolutions());
                assertEquals(12, cp.dfSearch(Searches.fds(new IntervalVar[][]{tasks}, x)).solve().numberOfSolutions());
            });
        }

        // 2. Raw CPSolver FDS overloads
        CPSolver cp = makeSolver();
        CPIntVar[] rx = makeIntVarArray(cp, 3, 3);
        cp.post(allDifferent(rx));
        assertEquals(6, makeDfs(cp, Searches.fds(rx)).solve().numberOfSolutions());
        assertEquals(6, makeDfs(cp, Searches.fds(new CPIntVar[][]{rx})).solve().numberOfSolutions());

        CPSolver cp2 = makeSolver();
        CPIntervalVar rt0 = makeIntervalVar(cp2, false, 2);
        rt0.setEndMax(4);
        CPIntervalVar rt1 = makeIntervalVar(cp2, false, 2);
        rt1.setEndMax(4);
        CPIntervalVar[] rTasks = new CPIntervalVar[]{rt0, rt1};
        cp2.post(new NoOverlap(rTasks));
        assertEquals(2, makeDfs(cp2, Searches.fds(rTasks)).solve().numberOfSolutions());
        assertEquals(2, makeDfs(cp2, Searches.fds(new CPIntervalVar[][]{rTasks})).solve().numberOfSolutions());
        assertEquals(2, makeDfs(cp2, Searches.fds(rTasks, new CPIntVar[0])).solve().numberOfSolutions());
        assertEquals(2, makeDfs(cp2, Searches.fds(new CPIntervalVar[][]{rTasks}, new CPIntVar[0])).solve().numberOfSolutions());

        // 3. SeqVar nodeSelector and firstFailBinary(SeqVar...)
        try (ModelDispatcher model = Factory.makeModelDispatcher()) {
            SeqVar seq1 = model.seqVar(4, 0, 3);
            SeqVar seq2 = model.seqVar(4, 0, 3);
            model.add(Factory.require(seq1, 1));
            model.add(Factory.exclude(seq1, 2));
            model.add(Factory.require(seq2, 2));
            model.add(Factory.exclude(seq2, 1));

            model.runCP(concrete -> {
                DFSearch dfs = concrete.dfSearch(Searches.firstFailBinary(seq1, seq2));
                assertEquals(1, dfs.solve().numberOfSolutions());
            });
        }
    }

    @Test
    public void testSchedulingLNSAndRunnableSearches() throws Exception {
        // 1. BlackBoxSearch + SchedulingRunnableSearch + SchedulingLNSRunnableSearch with mandatory & optional intervals
        try (ModelDispatcher model = Factory.makeModelDispatcher()) {
            IntervalVar t0 = model.intervalVar(0, 12, 3, true);
            IntervalVar t1 = model.intervalVar(0, 12, 3, true);
            IntervalVar t2 = model.intervalVar(0, 12, 3, true);
            IntervalVar opt3 = model.intervalVar(0, 12, 2, false);
            IntervalVar[] tasks = new IntervalVar[]{t0, t1, t2, opt3};
            model.add(Factory.noOverlap(tasks));

            IntVar mode = model.intVar(1, 3);
            IntExpression makespan = Factory.max(
                    Factory.endOr(t0, 0),
                    Factory.endOr(t1, 0),
                    Factory.endOr(t2, 0),
                    Factory.endOr(opt3, 0),
                    mode
            );
            org.maxicp.modeling.symbolic.Objective symObj = model.minimize(makespan);

            BlackBoxSearch bbs = new BlackBoxSearch(model, symObj);
            SchedulingRunnableSearch fdsPhase = new SchedulingRunnableSearch(
                    bbs, model, List.of(tasks), List.of(mode), symObj, 42L
            );
            SchedulingLNSRunnableSearch lnsPhase = new SchedulingLNSRunnableSearch(
                    bbs, model, List.of(tasks), List.of(mode), symObj, 15, 70, 42L
            );

            // First find an initial solution and run LNS iterations
            SearchStatistics stats = bbs.solve(s -> s.numberOfSolutions() >= 2 || s.numberOfFailures() >= 60);
            assertTrue(stats.numberOfSolutions() >= 1);

            // Also run fdsPhase and lnsPhase directly inside runCP with short budgets (in ms)
            model.runCP(cp -> {
                fdsPhase.run(60L);
                lnsPhase.run(80L);
            });
        }

        // 2. Mandatory-only intervals with SchedulingRunnableSearch and SchedulingLNSRunnableSearch
        try (ModelDispatcher model = Factory.makeModelDispatcher()) {
            IntervalVar t0 = model.intervalVar(0, 10, 2, true);
            IntervalVar t1 = model.intervalVar(0, 10, 2, true);
            IntervalVar t2 = model.intervalVar(0, 10, 2, true);
            IntervalVar[] tasks = new IntervalVar[]{t0, t1, t2};
            model.add(Factory.noOverlap(tasks));

            IntExpression makespan = Factory.max(Factory.endOr(t0, 0), Factory.endOr(t1, 0), Factory.endOr(t2, 0));
            org.maxicp.modeling.symbolic.Objective symObj = model.minimize(makespan);

            BlackBoxSearch bbs = new BlackBoxSearch(model, tasks);
            SchedulingRunnableSearch fdsPhase = new SchedulingRunnableSearch(
                    bbs, model, List.of(tasks), List.of(), symObj, 42L
            );
            SchedulingLNSRunnableSearch lnsPhase = new SchedulingLNSRunnableSearch(
                    bbs, model, List.of(tasks), List.of(), symObj, 10, 60, 42L
            );
            model.runCP(cp -> {
                fdsPhase.run(60L);
                lnsPhase.run(80L);
            });
            assertTrue(bbs.solve(s -> s.numberOfSolutions() >= 1).numberOfSolutions() >= 1);
        }
    }
}
