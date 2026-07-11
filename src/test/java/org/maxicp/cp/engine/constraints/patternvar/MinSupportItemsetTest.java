/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPItemsetVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.MiningIO;
import org.maxicp.cp.engine.core.mining.MiningTestUtils;
import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.search.DFSearch;
import org.maxicp.search.PatternSearches;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class MinSupportItemsetTest extends CPSolverTest {

    static final String PASQUIER = "data/MINING/ITEMSET/contextPasquier99.txt";
    static final String PASQUIER_ECLAT_2 = "data/MINING/ITEMSET/oracle/contextPasquier99-eclat-2.txt";

    /**
     * Enumerates all solutions of the model and returns them as a map
     * from canonical pattern string to support, asserting no duplicates.
     */
    static Map<String, Integer> enumerate(CPSolver cp, CPItemsetVar p, Supplier<Runnable[]> branching) {
        Map<String, Integer> sols = new HashMap<>();
        int[] items = new int[p.nItems()];
        DFSearch search = CPFactory.makeDfs(cp, branching);
        search.onSolution(() -> {
            assertTrue(p.isFixed());
            int n = p.fillRequired(items);
            String pattern = MiningTestUtils.canonical(items, n);
            assertNull(sols.put(pattern, p.coverSize()), "duplicate solution " + pattern);
        });
        SearchStatistics stats = search.solve();
        assertEquals(sols.size(), stats.numberOfSolutions());
        return sols;
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAgainstEclatReferenceFile(CPSolver cp) {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, db);
        cp.post(new MinSupportItemset(p, 2));

        Map<String, Integer> sols = enumerate(cp, p, PatternSearches.requireExclude(p));

        assertEquals(5, sols.remove("")); // the empty pattern covers all transactions
        assertEquals(MiningTestUtils.readSols(PASQUIER_ECLAT_2), sols);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMinExtensionSupportBranchingFindsSameSolutions(CPSolver cp) {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, db);
        cp.post(new MinSupportItemset(p, 2));

        Map<String, Integer> sols = enumerate(cp, p, PatternSearches.minExtensionSupport(p));

        assertEquals(5, sols.remove(""));
        assertEquals(MiningTestUtils.readSols(PASQUIER_ECLAT_2), sols);
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testAgainstBruteForceOnRandomDatabases(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 10; seed++) {
            Random rand = new Random(seed);
            TransactionDb db = MiningTestUtils.randomDb(rand, 1 + rand.nextInt(8), 2 + rand.nextInt(6), 0.5);
            int theta = 1 + rand.nextInt(3);
            Map<String, Integer> expected = MiningTestUtils.frequentItemsets(db, theta);

            CPSolver cp = solverSupplier.get();
            CPItemsetVar p = CPFactory.makeItemsetVar(cp, db);
            try {
                cp.post(new MinSupportItemset(p, theta));
            } catch (InconsistencyException e) {
                assertTrue(expected.isEmpty(), "seed " + seed);
                continue;
            }
            Map<String, Integer> sols = enumerate(cp, p, PatternSearches.requireExclude(p));
            assertEquals(expected, sols, "seed " + seed);
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInfeasibleThreshold(CPSolver cp) {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, db);
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new MinSupportItemset(p, 6)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRootFiltering(CPSolver cp) {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, db);
        cp.post(new MinSupportItemset(p, 2));
        // items 0 (support 0) and 4 (support 1) are infrequent
        assertTrue(p.isExcluded(0));
        assertTrue(p.isExcluded(4));
        assertEquals(4, p.nPossible());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCombinationWithSizeView(CPSolver cp) {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, db);
        cp.post(new MinSupportItemset(p, 2));
        p.size().removeBelow(2);
        cp.fixPoint();

        Map<String, Integer> sols = enumerate(cp, p, PatternSearches.requireExclude(p));

        Map<String, Integer> expected = new HashMap<>();
        MiningTestUtils.frequentItemsets(db, 2).forEach((pattern, support) -> {
            if (pattern.split(" ").length >= 2 && !pattern.isEmpty())
                expected.put(pattern, support);
        });
        assertEquals(expected, sols);
    }
}
