/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.cp.CPFactory;
import org.maxicp.search.Searches;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.maxicp.search.Searches.firstFailBinary;

public class CumulativeDecompTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAllDiffWithCumulative(CPSolver cp) {
        CPIntVar[] s = CPFactory.makeIntVarArray(cp, 5, 5);
        int[] d = new int[5];
        Arrays.fill(d, 1);
        int[] r = new int[5];
        Arrays.fill(r, 100);

        cp.post(new CumulativeDecomposition(s, d, r, 100));

        SearchStatistics stats = CPFactory.makeDfs(cp, Searches.firstFailBinary(s)).solve();

        assertEquals(120, stats.numberOfSolutions(), "cumulative alldiff expect makeIntVarArray permutations");
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBasic1(CPSolver cp) {
        CPIntVar[] s = CPFactory.makeIntVarArray(cp, 2, 10);
        int[] d = new int[]{5, 5};
        int[] r = new int[]{1, 1};

        cp.post(new CumulativeDecomposition(s, d, r, 1));
        cp.post(CPFactory.eq(s[0], 0));

        assertEquals(5, s[1].min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBasic2(CPSolver cp) {
        CPIntVar[] s = CPFactory.makeIntVarArray(cp, 2, 10);
        int[] d = new int[]{5, 5};
        int[] r = new int[]{1, 1};

        cp.post(new CumulativeDecomposition(s, d, r, 1));

        cp.post(CPFactory.eq(s[0], 5));

        assertEquals(0, s[1].max());
    }

    // not parametrized as the copy solver is quite slow for this test
    @Test
    public void testCapaOk() {
        CPSolver cp = CPFactory.makeSolver();

        CPIntVar[] s = CPFactory.makeIntVarArray(cp, 5, 10);
        int[] d = new int[]{5, 10, 3, 6, 1};
        int[] r = new int[]{3, 7, 1, 4, 8};

        cp.post(new CumulativeDecomposition(s, d, r, 12));

        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFailBinary(s));

        search.onSolution(() -> {
            //TODO : remove usage of Profile class in tests
            Profile.Rectangle[] rects = IntStream.range(0, s.length).mapToObj(i -> {
                int start = s[i].min();
                int end = start + d[i];
                int height = r[i];
                return new Profile.Rectangle(start, end, height);
            }).toArray(Profile.Rectangle[]::new);
            int[] discreteProfile = discreteProfile(rects);
            for (int h : discreteProfile) {
                assertTrue(h <= 12, "capa exceeded in cumulative constraint");
            }
        });

        SearchStatistics stats = search.solve();
        assertEquals(15649, stats.numberOfSolutions());
    }

    private static int[] discreteProfile(Profile.Rectangle... rectangles) {
        int min = Arrays.stream(rectangles).filter(r -> r.height() > 0).map(r -> r.start()).min(Integer::compare).get();
        int max = Arrays.stream(rectangles).filter(r -> r.height() > 0).map(r -> r.end()).max(Integer::compare).get();
        int[] heights = new int[max - min];
        // discrete profileRectangles of rectangles
        for (Profile.Rectangle r : rectangles) {
            if (r.height() > 0) {
                for (int i = r.start(); i < r.end(); i++) {
                    heights[i - min] += r.height();
                }
            }
        }
        return heights;
    }
}
