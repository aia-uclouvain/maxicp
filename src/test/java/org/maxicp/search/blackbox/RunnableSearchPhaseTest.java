package org.maxicp.search.blackbox;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.cp.examples.utils.TSPInstance;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.maxicp.modeling.Factory.*;

public class RunnableSearchPhaseTest {

    @Test
    public void lnsPhaseFindsOptimalSolutionOnTsp() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        TspModel tsp = buildTsp(model);

        SearchStatus status = model.runCP(() -> {
            BlackBoxSearch blackBoxSearch = new BlackBoxSearch(model, tsp.successor(), tsp.objective());
            LNSRunnableSearch lns = new LNSRunnableSearch(
                    blackBoxSearch,
                    model,
                    Arrays.asList(tsp.successor()),
                    tsp.objective(),
                    100,
                    80,
                    0.0,
                    123L
            );
            blackBoxSearch.addPhase("lns-improvement", lns, 1.0, true);
            int n = tsp.successor.length;
            List<Integer> best = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                best.add((i + 1) % n);
            }
            // succ = 0 -> 1-> ... -> n-1 -> 0  (a solution is required to start LNS)
            blackBoxSearch.updateSolution(best);
            blackBoxSearch.withVerbosity(BlackBoxSearch.Verbosity.QUIET);
            SearchStatus phaseStatus = blackBoxSearch.start(1);

            assertEquals(2707, blackBoxSearch.bestObjectiveValue().orElseThrow());
            return phaseStatus;
        });

        assertEquals(SearchStatus.IMPROVED, status);
    }

    @Test
    public void dfsPhaseClosesMagicSquareSearch() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar[] vars = buildMagicSquare(model, 3);

        SearchStatus status = model.runCP(() -> {
            BlackBoxSearch blackBoxSearch = new BlackBoxSearch(model, vars);
            DFSRunnableSearch dfs = new DFSRunnableSearch(
                    blackBoxSearch,
                    model,
                    Arrays.asList(vars),
                    null,
                    0.0,
                    42L
            );
            blackBoxSearch.addPhase("dfs", dfs, 1.0, false);
            blackBoxSearch.withVerbosity(BlackBoxSearch.Verbosity.QUIET);
            SearchStatus phaseStatus = blackBoxSearch.start(5);
            assertTrue(blackBoxSearch.bestSolution().isPresent());
            return phaseStatus;
        });

        assertEquals(SearchStatus.SAT, status);
    }

    @Test
    public void restartPhaseFindsFeasibleMagicSquareSolution() {
        ModelDispatcher model = Factory.makeModelDispatcher();
        IntVar[] vars = buildMagicSquare(model, 6);

        SearchStatus status = model.runCP(() -> {
            BlackBoxSearch blackBoxSearch = new BlackBoxSearch(model, vars);
            RestartRunnableSearch restart = new RestartRunnableSearch(
                    blackBoxSearch,
                    model,
                    Arrays.asList(vars),
                    64,
                    0.3,
                    42L
            );

            blackBoxSearch.addPhase("restart", restart, 1.0, false);
            blackBoxSearch.withVerbosity(BlackBoxSearch.Verbosity.QUIET);
            SearchStatus phaseStatus = blackBoxSearch.start(10);
            assertTrue(blackBoxSearch.bestSolution().isPresent());
            return phaseStatus;
        });

        assertEquals(SearchStatus.SAT, status);
    }

    private static TspModel buildTsp(ModelDispatcher model) {

        TSPInstance instance = new TSPInstance("src/test/resources/TSP/gr21.xml");

        int n = instance.n;
        int[][] distanceMatrix = instance.distanceMatrix;

        // successor of each city in a TSP tour
        IntVar[] successor = model.intVarArray(n, n);
        // distance between a city and its successor
        IntExpression[] distToSuccessor = IntStream.range(0, n)
                .mapToObj(city -> get(distanceMatrix[city], successor[city]))
                .toArray(IntExpression[]::new);
        // the successors must define a circuit between all cities
        model.add(circuit(successor));
        // objective is the sum of distances
        IntExpression totalDist = sum(distToSuccessor);
        // objective needs to be minimized
        Objective minimizeDistance = minimize(totalDist);
        return new TspModel(successor, minimizeDistance);
    }

    private static IntVar[] buildMagicSquare(ModelDispatcher model, int n) {
        int magicSum = n * (n * n + 1) / 2;
        IntVar[][] x = new IntVar[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                x[i][j] = model.intVar(1, n * n);
            }
        }

        IntVar[] flat = new IntVar[n * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(x[i], 0, flat, i * n, n);
        }
        model.add(Factory.allDifferent(flat));

        for (int i = 0; i < n; i++) {
            model.add(eq(sum(x[i]), magicSum));
        }
        for (int j = 0; j < n; j++) {
            IntVar[] column = new IntVar[n];
            for (int i = 0; i < n; i++) {
                column[i] = x[i][j];
            }
            model.add(eq(sum(column), magicSum));
        }

        IntVar[] diagonalLeft = new IntVar[n];
        IntVar[] diagonalRight = new IntVar[n];
        for (int i = 0; i < n; i++) {
            diagonalLeft[i] = x[i][i];
            diagonalRight[i] = x[n - i - 1][i];
        }
        model.add(eq(sum(diagonalLeft), magicSum));
        model.add(eq(sum(diagonalRight), magicSum));

        return flat;
    }

    private record TspModel(IntVar[] successor, Objective objective) {
    }
}
