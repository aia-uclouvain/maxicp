package org.maxicp.search.blackbox;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

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
}

