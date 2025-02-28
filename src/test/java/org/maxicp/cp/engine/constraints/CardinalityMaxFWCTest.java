/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class CardinalityMaxFWCTest extends CPSolverTest {


    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {

        Set<Integer>[] initialDomains = new Set[]{Set.of(0, 1, 2, 3), Set.of(0, 1, 2, 3), Set.of(2), Set.of(2), Set.of(0, 1, 2, 3)};
        CPIntVar[] x = CPFactory.makeIntVarArray(initialDomains.length, i -> CPFactory.makeIntVar(cp, initialDomains[i]));

        try {
            cp.post(new CardinalityMaxFWC(x, new int[]{2, 2, 2, 2}));

            assertEquals(3, x[0].size());
            assertEquals(3, x[1].size());
            assertEquals(3, x[4].size());


            assertTrue(!x[0].contains(2));
            assertTrue(!x[1].contains(2));
            assertTrue(!x[4].contains(2));

            x[0].fix(3);
            x[4].fix(3);
            cp.fixPoint();

            assertEquals(2, x[1].size());
            assertTrue(x[1].contains(0));
            assertTrue(x[1].contains(1));

        } catch (Exception e) {
            fail("should not fail");
        }
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRandomDecomp(CPSolver cp) {

        Set<Integer>[] initialDomains = new Set[]{
                Set.of(0, 1, 2, 3),
                Set.of(0, 1, 2, 3),
                Set.of(0, 1, 2, 3, 4, 5),
                Set.of(2, 5),
                Set.of(0, 1, 2, 3)};
        CPIntVar[] x = CPFactory.makeIntVarArray(initialDomains.length, i -> CPFactory.makeIntVar(cp, initialDomains[i]));

        CPIntVar[] count = CPFactory.makeIntVarArray(cp, 4, 5);
        int[] maxCard = new int[]{2, 3, 2, 2};

        // -----------------------------

        cp.getStateManager().saveState();

        cp.post(new CardinalityMaxFWC(x, maxCard));


        DFSearch search = CPFactory.makeDfs(cp, Searches.staticOrder(x));
        SearchStatistics stats1 = search.solve();


        // -----------------------------

        cp.getStateManager().restoreState();

        for (int i = 0; i < 4; i++) {
            int v = i;
            cp.post(CPFactory.equal(count[i], CPFactory.sum(CPFactory.makeIntVarArray(x.length, j -> CPFactory.isEqual(x[j], v)))));
            cp.post(CPFactory.lessOrEqual(count[i], maxCard[v]));
        }

        search = CPFactory.makeDfs(cp, Searches.staticOrder(x));
        SearchStatistics stats2 = search.solve();

        assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());


    }
}