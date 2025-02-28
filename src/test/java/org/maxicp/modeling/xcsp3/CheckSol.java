package org.maxicp.modeling.xcsp3;

import org.junit.Assume;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.modeling.algebra.bool.NotEq;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
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
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeNoException;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

@RunWith(Parameterized.class)
public class CheckSol {

    @Parameterized.Parameters(name = "{0}")
    public static String[] data() {
        try {
            //TODO: @gderval fix this
            return Files.walk(Paths.get("/Users/gderval/Desktop/xcsp3/XCSP17")).filter(Files::isRegularFile)
                    .filter(x -> x.toString().contains("xml"))
                    .map(Path::toString).toArray(String[]::new);
        }
        catch (IOException ex) {
            assumeNoException(ex);
            return new String[]{};
        }
    }

    @Parameterized.Parameter
    public String filename;

    public static final ImmutableSet<String> ignored = ImmutableSet.of(
            "Hanoi-09.xml.lzma", //OOM due to TableCT
            "MagicSquare-6-table.xml.lzma", //OOM in the XCSP3 parser (org.xcsp.parser.XParser !)
            "Fischer-11-12-fair.xml.lzma",
            "Fischer-11-14-fair.xml.lzma",
            "Hanoi-08.xml.lzma", //TableCT
            "Steiner3-14.xml.lzma",
            "BinPacking-tab-sw120-41.xml.lzma",
            "KnightTour-15-ext06.xml.lzma",
            "RoomMate-sr0700d-int.xml.lzma",
            "RoomMate-sr1000c-int.xml.lzma",
            "RoomMate-sr1000e-int.xml.lzma",
            "RoomMate-sr1000b-int.xml.lzma",
            "RoomMate-sr1000-int.xml.lzma",
            "GraphColoring-wap01a.xml.lzma",
            "GraphColoring-dsjc-1000-9.xml.lzma",
            "Subisomorphism-si4-r005-m600-07.xml.lzma"
    );

    public void checkIgnored() {
        String[] fname = filename.split("/");
        Assume.assumeTrue("Instance has been blacklisted", !ignored.contains(fname[fname.length-1]));
        Assume.assumeTrue("Instance has been blacklisted", !fname[fname.length-1].contains("Subisomorphism"));
        Assume.assumeTrue("Instance has been blacklisted", !fname[fname.length-1].contains("OpenStacks-m2c"));
    }

    @Test
    public void checkSol() throws Exception {
        checkIgnored();
        try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(filename)) {
            IntExpression[] q = instance.decisionVars();
            Supplier<Runnable[]> branching = () -> {
                int idx = -1; // index of the first variable that is not fixed
                for (int k = 0; k < q.length; k++)
                    if (!q[k].isFixed()) {
                        idx=k;
                        break;
                    }
                if (idx == -1)
                    return EMPTY;
                else {
                    IntExpression qi = q[idx];
                    int v = qi.min();
                    Runnable left = () -> instance.md().add(new Eq(qi, v));
                    Runnable right = () -> instance.md().add(new NotEq(qi, v));
                    return branch(left,right);
                }
            };

            long start = System.currentTimeMillis();

            instance.md().runCP((cp) -> {
                DFSearch search = cp.dfSearch(branching);
                LinkedList<String> sols = new LinkedList<>();
                search.onSolution(() -> {
                    String sol = instance.solutionGenerator().get();
                    sols.add(sol);
                    System.out.println(sol);
                });
                search.solve(limit -> {
                    Assume.assumeTrue("Too slow", (System.currentTimeMillis() - start) < 10000);
                    return limit.numberOfSolutions() == 1;
                });
                for(String sol: sols) {
                    try {
                        SolutionChecker sc = new SolutionChecker(false, filename, new ByteArrayInputStream(sol.getBytes()));
                        assertEquals(0, sc.invalidObjs.size());
                        assertEquals(0, sc.violatedCtrs.size());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
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
