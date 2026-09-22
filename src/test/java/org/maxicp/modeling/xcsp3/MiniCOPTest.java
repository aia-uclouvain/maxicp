package org.maxicp.modeling.xcsp3;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

@RunWith(Parameterized.class)
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

    @Parameterized.Parameters(name = "{0}")
    public static String[] data() {
        try {
            Path base = Paths.get("src/test/resources/XCSP3.MINICOP");
            return Files.walk(base)
                    .filter(Files::isRegularFile)
                    .filter(x -> x.toString().endsWith(".xml"))
                    .map(Path::toString).toArray(String[]::new);
        } catch (IOException ex) {
            assumeNoException(ex);
            return new String[]{};
        }
    }

    @Parameterized.Parameter
    public String filename;

    private static void assertValidSolution(String path, String sol) {
        try {
            SolutionChecker sc = new SolutionChecker(false, path, new ByteArrayInputStream(sol.getBytes()));
            assertEquals(0, sc.invalidObjs.size());
            assertEquals(0, sc.violatedCtrs.size());
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    @Test
    public void checkSol() throws Exception {
        try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(filename)) {
            IntExpression[] q = instance.decisionVars();
            long start = System.currentTimeMillis();
            String base = Paths.get(filename).getFileName().toString();

            assertTrue("Missing expected objective for " + base,
                    EXPECTED_OBJECTIVE_BY_FILE.containsKey(base));
            assertTrue("Missing objective in loaded COP instance: " + base,
                    instance.objective() != null);

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
                            Assume.assumeTrue("Too slow", (System.currentTimeMillis() - start) < 10000);
                            return false;
                        })
                        : search.solve(limit -> {
                            Assume.assumeTrue("Too slow", (System.currentTimeMillis() - start) < 10000);
                            return false;
                        });

                assertTrue("No solution found for " + filename, stats.numberOfSolutions() > 0);
                assertTrue("Search did not complete for " + base, stats.isCompleted());
                assertEquals("Wrong objective for " + base,
                        (int) EXPECTED_OBJECTIVE_BY_FILE.get(base), bestObjective[0]);
            });
        } catch (NotImplementedException ex) {
            Assume.assumeNoException(ex);
        } catch (InconsistencyException ex) {
            Assume.assumeNoException("Inconsistent", ex);
        } finally {
            System.gc();
        }
    }
}

