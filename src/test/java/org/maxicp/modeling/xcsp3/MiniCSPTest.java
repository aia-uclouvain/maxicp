package org.maxicp.modeling.xcsp3;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.maxicp.modeling.algebra.integer.IntExpression;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

@RunWith(Parameterized.class)
public class MiniCSPTest {

    @Parameterized.Parameters(name = "{0}")
    public static String[] data() {
        try {
            Path base = Paths.get("src/test/resources/XCSP3/MINICSP");
            return Files.walk(base)
                    .filter(Files::isRegularFile)
                    .filter(x -> x.toString().endsWith(".xml"))
                    .map(Path::toString).toArray(String[]::new);
        }
        catch (IOException ex) {
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

    @Test
    public void checkSol() throws Exception {
        try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(filename)) {
            IntExpression[] q = instance.decisionVars();
            long start = System.currentTimeMillis();

            instance.md().runCP((cp) -> {
                DFSearch search = cp.dfSearch(new FDSModeling(q));
                search.onSolution(() -> assertValidSolution(filename, instance.solutionGenerator().get()));
                SearchStatistics stats = search.solve(limit -> {
                    Assume.assumeTrue("Too slow", (System.currentTimeMillis() - start) < 10000);
                    return limit.numberOfSolutions() > 0;
                });
                assertTrue("No solution found for " + filename, stats.numberOfSolutions() > 0);
            });
        }
        catch (NotImplementedException ex) {
            Assume.assumeNoException(ex);
        }
        catch (InconsistencyException ex) {
            Assume.assumeNoException("Inconsistent", ex);
        }
        finally {
            System.gc();
        }
    }
}

