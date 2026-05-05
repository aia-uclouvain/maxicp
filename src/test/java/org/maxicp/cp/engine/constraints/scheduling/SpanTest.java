package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

public class SpanTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBasic(CPSolver cp) {
        CPIntervalVar span = makeIntervalVar(cp);
        span.setStartMin(10);
        span.setEndMax(30);
        CPIntervalVar[] alts = {
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp)
        };

        cp.post(span(span, alts));
        cp.fixPoint();

        assertTrue(span.isOptional());
        assertTrue(alts[0].isOptional());
        assertEquals(10, alts[0].startMin());
        assertEquals(30, alts[0].endMax());
        assertTrue(alts[1].isOptional());
        assertEquals(10, alts[1].startMin());
        assertEquals(30, alts[1].endMax());
        assertTrue(alts[2].isOptional());
        assertEquals(10, alts[2].startMin());
        assertEquals(30, alts[2].endMax());
        assertTrue(alts[3].isOptional());
        assertEquals(10, alts[3].startMin());
        assertEquals(30, alts[3].endMax());

        alts[0].setStartMin(15);
        alts[1].setStartMin(16);
        alts[2].setStartMin(17);
        alts[3].setStartMin(18);
        cp.fixPoint();
        assertEquals(15, span.startMin());

        alts[0].setEndMax(22);
        alts[1].setEndMax(23);
        alts[2].setEndMax(24);
        alts[3].setEndMax(25);
        cp.fixPoint();
        assertEquals(25, span.endMax());

        alts[1].setPresent();
        cp.fixPoint();
        assertTrue(span.isPresent());
        assertEquals(15, span.startMin());
        assertEquals(25, span.endMax());

        alts[2].setAbsent();
        alts[3].setAbsent();
        cp.fixPoint();
        assertEquals(15, span.startMin());
        assertEquals(23, span.endMax());

        alts[0].setAbsent();
        cp.fixPoint();
        assertEquals(16, span.startMin());
        assertEquals(23, span.endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAbsent1(CPSolver cp) {
        CPIntervalVar span = makeIntervalVar(cp);
        span.setStartMin(10);
        span.setEndMax(30);
        CPIntervalVar[] alts = {
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp)
        };

        cp.post(span(span, alts));

        span.setAbsent();
        cp.fixPoint();
        for(CPIntervalVar alt: alts) assertTrue(alt.isAbsent());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAbsent2(CPSolver cp) {
        CPIntervalVar span = makeIntervalVar(cp);
        span.setStartMin(10);
        span.setEndMax(30);
        CPIntervalVar[] alts = {
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp)
        };

        cp.post(span(span, alts));
        cp.fixPoint();
        for(CPIntervalVar alt: alts) assertFalse(alt.isAbsent());
        assertFalse(span.isAbsent());

        for(int i = 0; i < alts.length; i++) {
            CPIntervalVar alt = alts[i];
            alt.setAbsent();
            cp.fixPoint();
            if (i != alts.length - 1)
                assertFalse(span.isAbsent());
            else
                assertTrue(span.isAbsent());
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testPresent(CPSolver cp) {
        CPIntervalVar span = makeIntervalVar(cp);
        span.setStartMin(10);
        span.setEndMax(30);
        CPIntervalVar[] alts = {
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp)
        };

        cp.post(span(span, alts));
        cp.fixPoint();
        for(CPIntervalVar alt: alts) assertTrue(alt.isOptional());
        assertTrue(span.isOptional());

        span.setPresent();
        alts[0].setAbsent();
        alts[1].setAbsent();
        alts[3].setAbsent();
        cp.fixPoint();
        assertTrue(alts[2].isPresent());
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "2", "42", "67"})
    public void testDFS(long seed) {
        CPSolver cp = makeSolver();
        CPIntervalVar span = makeIntervalVar(cp);
        span.setStartMin(0);
        span.setEndMax(2);
        CPIntervalVar[] alts = {
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp),
        };
        for (CPIntervalVar alt: alts) {
            alt.setLength(1);
        }
        cp.post(span(span, alts));

        // shuffle using different random seeds, to exercise the search
        CPIntervalVar[] vars = new CPIntervalVar[] {span, alts[0], alts[1], alts[2]};
        List<CPIntervalVar> list = Arrays.asList(vars);
        Collections.shuffle(list, new Random(seed));
        vars = list.toArray(new CPIntervalVar[0]);

        DFSearch dfs = CPFactory.makeDfs(cp, and(branchOnStatus(vars), branchOnPresentStarts(vars)));
        dfs.onSolution(() -> {
            /*
            for (CPIntervalVar alt: alts) {
                if (alt.isAbsent()) {
                    System.out.print("-");
                } else {
                    System.out.print(alt.startMin());
                }
            }
            if (span.isAbsent()) {
                System.out.println(": -");
            } else {
                System.out.println(": " + span.startMin());
            }
             */

            if (span.isPresent()) {
                int spanStart = span.startMin();
                int spanEnd = span.endMax();
                int startMatch = 0;
                int endMatch = 0;
                int nPresent = 0;
                for (CPIntervalVar alt: alts) {
                    if (alt.isPresent()) {
                        nPresent++;
                        assertTrue(alt.startMin() >= spanStart);
                        assertTrue(alt.endMax() <= spanEnd);
                        if (alt.startMin() == spanStart)
                            startMatch++;
                        if (alt.endMax() == spanEnd)
                            endMatch++;
                    }
                }
                assertTrue(nPresent >= 1);
                assertTrue(startMatch >= 1);
                assertTrue(endMatch >= 1);
            } else {
                for (CPIntervalVar alt: alts) {
                    assertTrue(alt.isAbsent());
                }
            }
        });

        SearchStatistics stats = dfs.solve();
        // solutions:
        // number of selections of 0,1,2,3 elements among 3 alts (for presence)
        // for each element selected, 2 start values are feasible ({0, 1})
        int nSols = 0;
        for (int k = 0 ; k <= alts.length; k++) {
            int nSolsK = nCk(alts.length, k) * (int) Math.pow(2, k);
            nSols += nSolsK;
        }
        assertEquals(nSols, stats.numberOfSolutions());
    }

    private static int fact(int i) {
        if (i <= 1)
            return 1;
        return i * fact(i - 1);
    }

    /**
     * Number of ways to choose k elements in a set of n elements
     * @param n number of items in the set
     * @param k number of items to pick
     * @return Number of ways to choose k elements in a set of n elements
     */
    private static int nCk(int n, int k) {
        return fact(n) / (fact(k) * fact(n-k));
    }

}
