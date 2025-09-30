/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.Random;
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
    public void testDecomp1(CPSolver cp) {

        Set<Integer>[] initialDomains = new Set[]{Set.of(0, 1, 2, 3), Set.of(0, 1, 2, 3), Set.of(0, 1, 2, 3, 4, 5), Set.of(2, 5), Set.of(0, 1, 2, 3)};
        CPIntVar[] x = CPFactory.makeIntVarArray(initialDomains.length, i -> CPFactory.makeIntVar(cp, initialDomains[i]));

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
            cp.post(CPFactory.ge(CPFactory.sum(CPFactory.makeIntVarArray(x.length, j -> CPFactory.isEq(x[j], v))), minCard[v]));
        }

        search = CPFactory.makeDfs(cp, Searches.staticOrder(x));
        SearchStatistics stats2 = search.solve();

        assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());

    }

    /**
     * Generates a random set of integers from 0 to maxVal with given density
     */
    private Set<Integer> randomSet(int maxVal, double density, Random rand) {
        Set<Integer> s = new java.util.HashSet<>();
        for (int v = 0; v <= maxVal; v++) {
            if (rand.nextDouble() < density) {
                s.add(v);
            }
        }
        if (s.isEmpty()) s.add(rand.nextInt(maxVal + 1));
        return s;
    }




    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRandomDecomp2(CPSolver cp) {


        int n = 6;
        int d = 4;
        double density = 0.5;

        for (int iter = 0; iter < 20; iter++) {

            java.util.Random rand = new java.util.Random(iter);

            Set<Integer>[] initialDomains = new Set[n];
            for (int i = 0; i < n; i++) {
                initialDomains[i] = randomSet(d, density, rand);
            }
            CPIntVar[] x = CPFactory.makeIntVarArray(n, i -> CPFactory.makeIntVar(cp, initialDomains[i]));
            int[] minCard  = new int[d];
            // random cardinality

            for (int v = 0; v < d; v++) {
                minCard[v] = rand.nextInt(n / d);
            }

            // ------------------- with global constraint ----------------

            cp.getStateManager().saveState();

            cp.post(new CardinalityMinFWC(x, minCard));

            DFSearch search = CPFactory.makeDfs(cp, Searches.staticOrder(x));
            SearchStatistics stats1 = search.solve();

            // ------------------- decomposition ----------------

            cp.getStateManager().restoreState();

            for (int i = 0; i < 4; i++) {
                int v = i;
                cp.post(CPFactory.ge(CPFactory.sum(CPFactory.makeIntVarArray(x.length, j -> CPFactory.isEq(x[j], v))), minCard[v]));
            }

            search = CPFactory.makeDfs(cp, Searches.staticOrder(x));
            SearchStatistics stats2 = search.solve();

            // compare number of solutions

            assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());

        }





    }
}