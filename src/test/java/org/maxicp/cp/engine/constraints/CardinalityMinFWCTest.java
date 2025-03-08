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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class CardinalityMinFWCTest extends CPSolverTest {


    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {

        Set<Integer>[] initialDomains = new Set[]{
                Set.of(0, 2, 3),
                Set.of(0, 1, 2, 3),
                Set.of(0, 3),
                Set.of(0, 2, 3),
                Set.of(0, 2, 3)};
        CPIntVar[] x = CPFactory.makeIntVarArray(initialDomains.length, i -> CPFactory.makeIntVar(cp, initialDomains[i]));

        try {
            cp.post(new CardinalityMinFWC(x, new int[]{0, 1, 2, 0}));

            assertEquals(1, x[1].size());
            assertEquals(1, x[1].min());

            x[4].remove(2);
            cp.fixPoint();

            assertEquals(2, x[0].min());
            assertEquals(1, x[0].size());

            assertEquals(2, x[0].min());
            assertEquals(1, x[1].size());
            assertEquals(2, x[3].min());
            assertEquals(1, x[3].size());

            assertEquals(2, x[4].size());


        } catch (Exception e) {
            fail("should not fail: " + e);
        }
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRandomDecomp(CPSolver cp) {

        Set<Integer>[] initialDomains = new Set[]{Set.of(0, 1, 2, 3), Set.of(0, 1, 2, 3), Set.of(0, 1, 2, 3, 4, 5), Set.of(2, 5), Set.of(0, 1, 2, 3)};
        CPIntVar[] x = CPFactory.makeIntVarArray(initialDomains.length, i -> CPFactory.makeIntVar(cp, initialDomains[i]));

        CPIntVar[] count = CPFactory.makeIntVarArray(cp, 4, 5);
        int[] minCard = new int[]{0, 1, 2, 0};

        // -----------------------------

        cp.getStateManager().saveState();

        cp.post(new CardinalityMinFWC(x, minCard));

        DFSearch search = CPFactory.makeDfs(cp, Searches.staticOrder(x));
        SearchStatistics stats1 = search.solve();

        // -----------------------------

        cp.getStateManager().restoreState();

        for (int i = 0; i < 4; i++) {
            int v = i;
            cp.post(CPFactory.eq(count[i], CPFactory.sum(CPFactory.makeIntVarArray(x.length, j -> CPFactory.isEq(x[j], v)))));
            cp.post(CPFactory.ge(count[i], minCard[v]));
        }

        search = CPFactory.makeDfs(cp, Searches.staticOrder(x));
        SearchStatistics stats2 = search.solve();

        assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());

    }
}