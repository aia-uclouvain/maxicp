package org.maxicp.xcsp3;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.ImmutableSet;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.util.exception.NotImplementedException;
import org.xcsp.parser.callbacks.SolutionChecker;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeNoException;
import static org.maxicp.search.Searches.selectMin;

@RunWith(Parameterized.class)
public class CheckSolveCOPMini {

    @Parameterized.Parameters(name = "{0}")
    public static String[] data() {
        try {
            //TODO: @gderval fix this
            return Files.walk(Paths.get("data/XCSP3/tests/miniCOP")).filter(Files::isRegularFile)
                    .filter(x -> x.toString().contains("xml"))
                    .map(Path::toString).toArray(String[]::new);
        } catch (IOException ex) {
            assumeNoException(ex);
            return new String[]{};
        }
    }

    @Parameterized.Parameter
    public String filename;

    public static final ImmutableSet<String> ignored = ImmutableSet.of(
            "Hanoi-09.xml.lzma" //OOM due to TableCT
    );

    public void checkIgnored() {
        String[] fname = filename.split("/");
        Assume.assumeTrue("Instance has been blacklisted", !ignored.contains(fname[fname.length - 1]));
    }

    public static Supplier<IntExpression> customVariableSelector(IntExpression... xs) {
        return () -> {
            return selectMin(xs, xi -> !xi.isFixed(), IntExpression::size);
        };
    }

    @Test
    public void checkSol() throws Exception {
        checkIgnored();
        try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(filename)) {
            IntExpression[] x = instance.decisionVars();
            long start = System.currentTimeMillis();

            instance.md().runCP((cp) -> {
                Random r = new Random();
                DFSearch search = cp.dfSearch(Searches.conflictOrderingSearch(customVariableSelector(x), var -> {
                    return var.min();
                }));
                LinkedList<String> sols = new LinkedList<>();
                search.onSolution(() -> {
                    System.out.println("Found a solution");
                    try {
                        System.out.println("Objective:" + instance.objective().get().evaluate());
                    } catch (VariableNotFixedException e) {
                        throw new RuntimeException(e);
                    }
                    String sol = instance.solutionGenerator().get();
                    sols.add(sol);
                    //System.out.println(sol);
                });
                SearchStatistics stats = search.optimize(instance.objective().get(), limit -> {
                    Assume.assumeTrue("Too slow", (System.currentTimeMillis() - start) < 10000);
                    return false;
                });
                System.out.println(stats);
                for (String sol : sols) {
                    try {
                        SolutionChecker sc = new SolutionChecker(false, filename, new ByteArrayInputStream(sol.getBytes()));
                        assertEquals(0, sc.invalidObjs.size());
                        assertEquals(0, sc.violatedCtrs.size());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
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
