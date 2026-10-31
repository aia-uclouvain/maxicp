package org.maxicp.search.blackbox;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.SearchMethod;
import org.maxicp.search.SearchStatistics;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModelingBlackBoxTest {

    @Test
    public void optimizeOnModelingLayerFindsBestObjective() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar x = model.intVar(0, 2);
        IntVar y = model.intVar(0, 2);
        model.add(Factory.allDifferent(x, y));

        IntExpression objExpr = Factory.sum(x, y);
        Objective objective = model.minimize(objExpr);

        ModelingBlackBox.Result result = ModelingBlackBox.optimize(model, new IntExpression[]{x, y}, objective, 2);

        assertTrue(result.solution().isPresent());
        assertTrue(result.objectiveValue().isPresent());
        assertEquals(1, (int) result.objectiveValue().get());
        assertTrue(result.status() == SearchStatus.PROVEN_OPTIMAL || result.status() == SearchStatus.IMPROVED);
    }

    @Test
    public void solveOnModelingLayerReturnsSatisfiableStatus() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar x = model.intVar(0, 2);
        model.add(Factory.eq(x, 1));

        ModelingBlackBox.Result result = ModelingBlackBox.solve(model, new IntExpression[]{x}, 1);

        assertTrue(result.solution().isPresent());
        assertTrue(result.status() == SearchStatus.SAT || result.status() == SearchStatus.IMPROVED);
    }

    @Test
    public void solveAllowsConfiguringPhaseSharesAndRestartParameters() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar x = model.intVar(0, 10);
        model.add(Factory.eq(x, 7));

        ModelingBlackBox.Result result = ModelingBlackBox.solve(
                model,
                new IntExpression[]{x},
                1,
                cfg -> cfg
                        .restart(new BlackBoxSearch.RestartPhaseOptions(8, 0.7, 123L))
                        .exhaustive(new BlackBoxSearch.ExhaustivePhaseOptions(0.0, 999L))
                        .shares(0.5, 0.5)
        );

        assertTrue(result.solution().isPresent());
        assertTrue(result.status() == SearchStatus.SAT || result.status() == SearchStatus.PROVEN_OPTIMAL);
    }

    @Test
    public void optimizationStartsWithInitialExhaustiveAndStopsWhenProven() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar x = model.intVar(0, 1);
        Objective objective = model.minimize(x);

        SearchStatus status = model.runCP(() -> {
            BlackBoxSearch search = new BlackBoxSearch(model, new IntExpression[]{x}, objective)
                    .withOptimizationPlan(
                            BlackBoxSearch.RestartPhaseOptions.defaults(),
                            BlackBoxSearch.LnsPhaseOptions.defaults(),
                            BlackBoxSearch.ExhaustivePhaseOptions.defaults(),
                            0.25, 0.25, 0.25, 0.25
                    );
            SearchStatus st = search.start(1);
            assertEquals("initial-exhaustive", search.executedPhases().getFirst());
            assertEquals(1, search.executedPhases().size());
            return st;
        });

        assertEquals(SearchStatus.PROVEN_OPTIMAL, status);
    }

    @Test
    public void feasibilityStopsAsSoonAsFirstSolutionIsFound() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar x = model.intVar(0, 10);
        model.add(Factory.eq(x, 7));

        SearchStatus status = model.runCP(() -> {
            BlackBoxSearch search = new BlackBoxSearch(model, new IntExpression[]{x})
                    .withFeasibilityPlan(
                            BlackBoxSearch.RestartPhaseOptions.defaults(),
                            BlackBoxSearch.ExhaustivePhaseOptions.defaults(),
                            0.2, 0.4, 0.4
                    );
            SearchStatus st = search.start(1);
            assertEquals(1, search.executedPhases().size());
            return st;
        });

        assertEquals(SearchStatus.SAT, status);
    }

    @Test
    public void progressVerbosityLogsSolutionsAndObjectiveImprovements() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar x = model.intVar(0, 10);
        Objective objective = model.minimize(x);

        BlackBoxSearch search = new BlackBoxSearch(model, new IntExpression[]{x}, objective)
                .withVerbosity(BlackBoxSearch.Verbosity.PROGRESS);

        PrintStream previousOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            search.updateSolution(List.of(5));
            search.updateObjective(5);
            search.updateSolution(List.of(4));
            search.updateObjective(4);
        } finally {
            System.setOut(previousOut);
        }

        String output = captured.toString();
        assertTrue(output.contains("solution #1 found"));
        assertTrue(output.contains("solution #2 found"));
        assertTrue(output.contains("objective improved 5 -> 4"));
    }

    @Test
    public void lnsProgressLogsAreThrottledEveryHundredIterations() {
        assertTrue(LNSRunnableSearch.shouldLogProgressIteration(100));
        assertTrue(LNSRunnableSearch.shouldLogProgressIteration(200));
        assertTrue(!LNSRunnableSearch.shouldLogProgressIteration(1));
        assertTrue(!LNSRunnableSearch.shouldLogProgressIteration(99));
        assertTrue(!LNSRunnableSearch.shouldLogProgressIteration(101));
    }

    @Test
    public void solveReturnsUnsatOnRootInconsistentModel() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar x = model.intVar(0, 2);
        model.add(Factory.eq(x, 0));
        model.add(Factory.eq(x, 2));

        ModelingBlackBox.Result result = ModelingBlackBox.solve(model, new IntExpression[]{x}, 1);
        assertEquals(SearchStatus.UNSAT, result.status());
        assertTrue(result.solution().isEmpty());
    }

    @Test
    public void modelStructureAnalyzerIdentifiesPrimarySecondaryAndElementCosts() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar x = model.intVar(0, 2);
        IntVar y = model.intVar(0, 2);
        // z is functionally defined by x + y
        IntVar z = model.intVar(0, 4);
        // s is a degree-1 objective slack constrained by a table (x, s)
        IntVar s = model.intVar(0, 10);

        model.add(Factory.allDifferent(x, y));
        model.add(Factory.eq(z, Factory.sum(x, y)));
        model.add(new org.maxicp.modeling.constraints.Table(new IntExpression[]{x, s}, new int[][]{
                {0, 5}, {0, 8},
                {1, 2}, {1, 9},
                {2, 4}, {2, 7}
        }));

        int[] costTable = new int[]{30, 10, 20};
        IntExpression objExpr = Factory.sum(Factory.get(costTable, y), z, s);
        Objective objective = model.minimize(objExpr);

        IntExpression[] vars = new IntExpression[]{x, y, z, s};
        ModelStructureAnalyzer analyzer = ModelStructureAnalyzer.analyze(model, List.of(vars), objective);

        assertEquals(2, analyzer.primaryIndices().length);
        assertEquals(2, analyzer.secondaryIndices().length);
        assertEquals(1, analyzer.dominantSlackIndices().length);
        assertEquals(3, analyzer.dominantSlackIndices()[0]); // s is index 3

        // Objective polarity for minimization of positive terms should be -1 (prefer smaller values)
        assertEquals(-1, analyzer.objectivePolarity(2));
        assertEquals(-1, analyzer.objectivePolarity(3));

        // Element1D cost array for y (index 1) should reflect costTable
        long[] yCosts = analyzer.objectiveValueCosts(1);
        assertEquals(30L, yCosts[0]);
        assertEquals(10L, yCosts[1]);
        assertEquals(20L, yCosts[2]);

        ModelingBlackBox.Result res = ModelingBlackBox.optimize(model, vars, objective, 2);
        assertEquals(SearchStatus.PROVEN_OPTIMAL, res.status());
        // Optimal: y=1 (cost 10), x=0 (z=1, s=5) -> total = 10 + 1 + 5 = 16, or y=1 (cost 10), x=2 (z=3, s=4) -> 17
        assertEquals(16, (int) res.objectiveValue().get());
    }

    @Test
    public void autoDiscoversDecisionVarsOnNQueensWithTopLevelSolveAndSearchMethod() {
        int n = 8;
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar[] q = model.intVarArray(n, n);
        IntExpression[] qL = model.intVarArray(n, i -> q[i].plus(i));
        IntExpression[] qR = model.intVarArray(n, i -> q[i].minus(i));

        model.add(Factory.allDifferent(q));
        model.add(Factory.allDifferent(qL));
        model.add(Factory.allDifferent(qR));

        AtomicInteger solCount = new AtomicInteger(0);
        model.runCP(cp -> {
            SearchMethod search = cp.blackBoxSearch();
            search.onSolution(() -> {
                solCount.incrementAndGet();
                Set<Integer> cols = new HashSet<>();
                for (int i = 0; i < n; i++) {
                    assertTrue(q[i].isFixed());
                    cols.add(q[i].min());
                }
                assertEquals(n, cols.size());
            });
            SearchStatistics stats = search.solve();
            assertTrue(stats.numberOfSolutions() >= 1);
        });
        assertEquals(1, solCount.get());

        SearchStatistics shortcutStats = model.solve(2);
        assertTrue(shortcutStats.numberOfSolutions() >= 1);
    }

    @Test
    public void autoDiscoversDecisionVarsOnLinearConstraintsWithoutGlobalConstraints() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar x = model.intVar(1, 5);
        IntVar y = model.intVar(1, 5);
        IntVar z = model.intVar(1, 5);
        // Only ExpressionIsTrue (eq, le, neq) constraints
        model.add(Factory.eq(Factory.sum(x, y, z), 9));
        model.add(Factory.neq(x, y));
        model.add(Factory.le(x, z));

        Objective obj = model.minimize(Factory.plus(Factory.mul(x, 10), y));
        ModelingBlackBox.Result res = ModelingBlackBox.optimize(model, obj, 2);
        assertEquals(SearchStatus.PROVEN_OPTIMAL, res.status());
        // x=1, z=5, y=3 -> sum=9, x!=y, x<=z, obj = 10*1 + 3 = 13 (since x=1, y=2 -> z=6 impossible; x=1, y=3 -> z=5 valid)
        assertEquals(13, res.objectiveValue().orElseThrow());
        assertEquals(List.of(1, 3, 5), res.values(x, y, z).orElseThrow());
    }

    @Test
    public void blackBoxSolvesJobShopSchedulingWithoutPassingVariables() {
        // 3-job 3-machine JobShop instance
        int nJobs = 3;
        int nMachines = 3;
        int[][] duration = {
                {3, 2, 2},
                {2, 1, 4},
                {4, 3, 1}
        };
        int[][] machine = {
                {0, 1, 2},
                {0, 2, 1},
                {1, 0, 2}
        };
        int horizon = 30;

        ModelDispatcher model = Factory.makeModelDispatcher();
        IntervalVar[][] activities = new IntervalVar[nJobs][nMachines];
        for (int i = 0; i < nJobs; i++) {
            for (int j = 0; j < nMachines; j++) {
                activities[i][j] = model.intervalVar(0, horizon, duration[i][j], true);
            }
        }
        for (int i = 0; i < nJobs; i++) {
            for (int j = 1; j < nMachines; j++) {
                model.add(Factory.endBeforeStart(activities[i][j - 1], activities[i][j]));
            }
        }
        for (int m = 0; m < nMachines; m++) {
            final int mach = m;
            IntervalVar[] onMachine = new IntervalVar[nJobs];
            for (int i = 0; i < nJobs; i++) {
                for (int j = 0; j < nMachines; j++) {
                    if (machine[i][j] == mach) {
                        onMachine[i] = activities[i][j];
                    }
                }
            }
            model.add(Factory.noOverlap(onMachine));
        }

        IntExpression makespan = Factory.max(
                Factory.endOr(activities[0][2], 0),
                Factory.endOr(activities[1][2], 0),
                Factory.endOr(activities[2][2], 0)
        );
        Objective minimizeMakespan = model.minimize(makespan);

        AtomicInteger bestMakespan = new AtomicInteger(Integer.MAX_VALUE);
        model.runCP(cp -> {
            BlackBoxSearch search = cp.blackBoxSearch();
            search.onSolution(() -> bestMakespan.set(makespan.min()));
            SearchStatistics stats = search.optimize(minimizeMakespan, 2);
            assertTrue(stats.numberOfSolutions() >= 1);
            assertEquals(SearchStatus.PROVEN_OPTIMAL, search.status());
        });
        assertEquals(10, bestMakespan.get());
    }

    @Test
    public void blackBoxSolvesRCPSPWithCumulFunctionsOutOfTheBox() {
        // 5 activities, resource capacity 4
        int[] duration = {0, 3, 2, 4, 0};
        int[] demand = {0, 2, 3, 2, 0};
        int capacity = 4;

        ModelDispatcher model = Factory.makeModelDispatcher();
        IntervalVar[] tasks = model.intervalVarArray(5, true);
        for (int i = 0; i < 5; i++) {
            model.add(Factory.length(tasks[i], duration[i]));
        }
        // Precedences: 0 -> (1, 2, 3) -> 4, and 1 -> 3
        model.add(Factory.endBeforeStart(tasks[0], tasks[1]));
        model.add(Factory.endBeforeStart(tasks[0], tasks[2]));
        model.add(Factory.endBeforeStart(tasks[0], tasks[3]));
        model.add(Factory.endBeforeStart(tasks[1], tasks[3]));
        model.add(Factory.endBeforeStart(tasks[1], tasks[4]));
        model.add(Factory.endBeforeStart(tasks[2], tasks[4]));
        model.add(Factory.endBeforeStart(tasks[3], tasks[4]));

        CumulFunction resource = Factory.flat();
        for (int i = 0; i < 5; i++) {
            if (demand[i] > 0) {
                resource = Factory.sum(resource, Factory.pulse(tasks[i], demand[i]));
            }
        }
        model.add(Factory.le(resource, capacity));

        IntExpression makespan = Factory.max(Arrays.stream(tasks).map(t -> Factory.endOr(t, 0)).toArray(IntExpression[]::new));
        Objective obj = model.minimize(makespan);

        ModelingBlackBox.Result res = ModelingBlackBox.optimize(model, obj, 2);
        assertEquals(SearchStatus.PROVEN_OPTIMAL, res.status());
        // tasks 1 (demand 2, dur 3) and 2 (demand 3, dur 2) cannot overlap (2+3=5 > 4).
        // If 2 runs [0,2], then 1 runs [2,5] and 3 runs [5,9] -> makespan 9.
        // If 1 runs [0,3], then 3 (dur 4, demand 2) runs [3,7], and 2 (dur 2, demand 3) cannot overlap with 1 or 3 -> runs [7,9] -> makespan 9.
        assertEquals(9, res.objectiveValue().orElseThrow());
    }

    @Test
    public void blackBoxSolvesFlexibleJobShopWithOptionalIntervalsAndAlternative() {
        // 2 jobs, each with 2 operations; 2 machines
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntervalVar[] tasks = new IntervalVar[4];
        IntervalVar[][] alt = new IntervalVar[4][2];
        int[][] dur = {
                {4, 2}, // job 0 op 0
                {3, 5}, // job 0 op 1
                {2, 6}, // job 1 op 0
                {4, 3}  // job 1 op 1
        };

        for (int t = 0; t < 4; t++) {
            for (int m = 0; m < 2; m++) {
                alt[t][m] = model.intervalVar(dur[t][m], false);
            }
            tasks[t] = model.intervalVar(true);
            model.add(Factory.alternative(tasks[t], alt[t]));
        }
        model.add(Factory.noOverlap(new IntervalVar[]{alt[0][0], alt[1][0], alt[2][0], alt[3][0]}));
        model.add(Factory.noOverlap(new IntervalVar[]{alt[0][1], alt[1][1], alt[2][1], alt[3][1]}));

        model.add(Factory.endBeforeStart(tasks[0], tasks[1]));
        model.add(Factory.endBeforeStart(tasks[2], tasks[3]));

        IntExpression makespan = Factory.max(Factory.endOr(tasks[1], 0), Factory.endOr(tasks[3], 0));
        Objective obj = model.minimize(makespan);

        AtomicInteger best = new AtomicInteger(Integer.MAX_VALUE);
        model.runCP(cp -> {
            BlackBoxSearch search = cp.blackBoxSearch();
            search.onSolution(() -> best.set(makespan.min()));
            search.optimize(obj, 2);
            assertEquals(SearchStatus.PROVEN_OPTIMAL, search.status());
        });
        // Job 0: op 0 on M1 [0,2], op 1 on M0 [2,5] -> ends at 5
        // Job 1: op 0 on M0 [0,2], op 1 on M1 [2,5] -> ends at 5
        assertEquals(5, best.get());
    }

    @Test
    public void rawCPSolverBlackBoxSolvesCombinatorialAndOptimizationModels() {
        CPSolver cp = CPFactory.makeSolver();
        int n = 4;
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, n, n);
        cp.post(CPFactory.allDifferent(x));

        int[][] cost = {
                {10, 2, 8, 7},
                {4, 12, 3, 9},
                {6, 5, 11, 1},
                {8, 3, 2, 10}
        };
        CPIntVar[] rowCosts = new CPIntVar[n];
        for (int i = 0; i < n; i++) {
            rowCosts[i] = CPFactory.element(cost[i], x[i]);
        }
        CPIntVar totalCost = CPFactory.sum(rowCosts);
        org.maxicp.search.Objective obj = cp.minimize(totalCost);

        AtomicInteger bestObj = new AtomicInteger(Integer.MAX_VALUE);
        BlackBoxSearch search = CPFactory.makeBlackBox(cp);
        search.onSolution(() -> bestObj.set(totalCost.min()));
        SearchStatistics stats = search.optimize(obj);
        assertTrue(stats.numberOfSolutions() >= 1);
        assertEquals(SearchStatus.PROVEN_OPTIMAL, search.status());
        // x = [1, 0, 3, 2] -> cost = 2 + 4 + 1 + 2 = 9
        assertEquals(9, bestObj.get());
    }

    @Test
    public void rawCPSolverBlackBoxSolvesSchedulingModelOutOfTheBox() {
        CPSolver cp = CPFactory.makeSolver();
        CPIntervalVar t0 = CPFactory.makeIntervalVar(cp, false, 3);
        CPIntervalVar t1 = CPFactory.makeIntervalVar(cp, false, 4);
        CPIntervalVar t2 = CPFactory.makeIntervalVar(cp, false, 2);

        cp.post(CPFactory.noOverlap(t0, t1));
        cp.post(CPFactory.endBeforeStart(t0, t2));

        CPIntVar makespan = CPFactory.makespan(t0, t1, t2);
        org.maxicp.search.Objective obj = cp.minimize(makespan);

        AtomicInteger bestMakespan = new AtomicInteger(Integer.MAX_VALUE);
        BlackBoxSearch search = cp.blackBoxSearch();
        search.onSolution(() -> bestMakespan.set(makespan.min()));
        SearchStatistics stats = search.optimize(obj, 2);
        assertTrue(stats.numberOfSolutions() >= 1);
        assertEquals(SearchStatus.PROVEN_OPTIMAL, search.status());
        // t0 [0,3] and t1 [3,7] on machine, t2 [3,5] after t0 -> makespan 7
        assertEquals(7, bestMakespan.get());
    }
}


