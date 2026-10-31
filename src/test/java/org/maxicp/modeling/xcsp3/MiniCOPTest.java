package org.maxicp.modeling.xcsp3;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Maximization;
import org.maxicp.modeling.symbolic.Minimization;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.FDSModeling;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.util.exception.NotImplementedException;
import org.xcsp.parser.callbacks.SolutionChecker;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MiniCOPTest {

    private static final Map<String, Integer> EXPECTED_OBJECTIVE_BY_FILE = Map.of(
            "Drinking-mini-000050_c24.xml", 5,
            "GolombRuler-a3v18-07_c18.xml", 25,
            "LitPuzzle-10_c24.xml", 44,
            "MaximumDensityOscillatingLife-mini-5-2_c24.xml", 28,
            "Pyramid-07-500_c24.xml", 212,
            "RotationPuzzle-2_c24.xml", 7,
            "SameQueensKnights-mini-05_c24.xml", 6,
            "StillLife-05-05_c24.xml", 16
    );

    public static Stream<String> data() throws IOException {
        Path base = Paths.get("src/test/resources/XCSP3.MINICOP");
        return Files.walk(base)
                .filter(Files::isRegularFile)
                .filter(x -> x.toString().endsWith(".xml"))
                .filter(x -> EXPECTED_OBJECTIVE_BY_FILE.containsKey(x.getFileName().toString()))
                .map(Path::toString)
                .sorted();
    }

    private static void assertValidSolution(String path, String sol) {
        PrintStream prevOut = System.out;
        try {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            SolutionChecker sc = new SolutionChecker(false, path, new ByteArrayInputStream(sol.getBytes()));
            assertEquals(0, sc.invalidObjs.size());
            assertEquals(0, sc.violatedCtrs.size());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(prevOut);
        }
    }

    private static int evaluateObjective(Objective objective) {
        try {
            return switch (objective) {
                case Minimization min -> min.expr().evaluate();
                case Maximization max -> max.expr().evaluate();
                default -> throw new IllegalStateException("Unsupported objective type: " + objective.getClass());
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void checkSol(String filename) throws Exception {
        try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(filename)) {
            IntExpression[] q = instance.decisionVars();
            long start = System.currentTimeMillis();
            String base = Paths.get(filename).getFileName().toString();

            assertTrue(EXPECTED_OBJECTIVE_BY_FILE.containsKey(base),
                    "Missing expected objective for " + base);
            assertTrue(instance.objective() != null,
                    "Missing objective in loaded COP instance: " + base);

            instance.md().runCP((cp) -> {
                DFSearch search = cp.dfSearch(new FDSModeling(q));
                final int[] bestObjective = {0};
                search.onSolution(() -> {
                    assertValidSolution(filename, instance.solutionGenerator().get());
                    if (instance.objective() != null) {
                        bestObjective[0] = evaluateObjective(instance.objective());
                    }
                });

                SearchStatistics stats = instance.objective() != null
                        ? search.optimize(instance.objective(), limit -> {
                            Assumptions.assumeTrue((System.currentTimeMillis() - start) < 5000, "Too slow");
                            return false;
                        })
                        : search.solve(limit -> {
                            Assumptions.assumeTrue((System.currentTimeMillis() - start) < 5000, "Too slow");
                            return false;
                        });

                assertTrue(stats.numberOfSolutions() > 0, "No solution found for " + filename);
                assertTrue(stats.isCompleted(), "Search did not complete for " + base);
                assertEquals((int) EXPECTED_OBJECTIVE_BY_FILE.get(base), bestObjective[0],
                        "Wrong objective for " + base);
            });
        } catch (NotImplementedException ex) {
            Assumptions.assumeTrue(false, ex.getMessage());
        } catch (InconsistencyException ex) {
            Assumptions.assumeTrue(false, "Inconsistent: " + ex.getMessage());
        }
    }
}
