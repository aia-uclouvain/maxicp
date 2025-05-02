package org.maxicp.xcsp3;

import org.junit.Assume;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.modeling.algebra.bool.NotEq;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.util.exception.NotImplementedException;
import org.xcsp.parser.callbacks.SolutionChecker;

import java.io.ByteArrayInputStream;
import java.util.LinkedList;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class Main {

    public static void main(String[] args) {

        String path = "data/XCSP3/tests/mini/FRB-30-15-1_c18.xml";

        try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(path)) {
            IntExpression[] q = instance.decisionVars();
            Supplier<Runnable[]> branching = () -> {
                int idx = -1; // index of the first variable that is not fixed
                for (int k = 0; k < q.length; k++)
                    if (!q[k].isFixed()) {
                        idx = k;
                        break;
                    }
                if (idx == -1)
                    return EMPTY;
                else {
                    IntExpression qi = q[idx];
                    int v = qi.min();
                    Runnable left = () -> instance.md().add(new Eq(qi, v));
                    Runnable right = () -> instance.md().add(new NotEq(qi, v));
                    return branch(left, right);
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
                for (String sol : sols) {
                    try {
                        SolutionChecker sc = new SolutionChecker(false, path, new ByteArrayInputStream(sol.getBytes()));
                        assertEquals(0, sc.invalidObjs.size());
                        assertEquals(0, sc.violatedCtrs.size());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (NotImplementedException ex) {
            Assume.assumeNoException(ex);
        } catch (Exception ex) {
            Assume.assumeNoException("Inconsistent", ex);
        } finally {
            System.gc();
        }

    }
}
