/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPItemsetVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.MiningIO;
import org.maxicp.cp.engine.core.mining.MiningTestUtils;
import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.cp.engine.core.mining.ZDCFunction;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.PatternSearches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.makeIntVar;

public class ZDCTest extends CPSolverTest {

    static final String LABELED_TEST = "data/MINING/ITEMSET/withlabels/test.txt";
    static final double SCALE = 10000;

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testScoreFixedWhenSupportsFixed(CPSolver cp) {
        // chi2(4, 0, 4, 4) = 8 => scaled score 8000
        CPIntVar p = makeIntVar(cp, 4, 4);
        CPIntVar n = makeIntVar(cp, 0, 0);
        CPIntVar score = makeIntVar(cp, 0, 1000000);
        cp.post(new ZDC(p, n, 4, 4, ZDCFunction.CHI2.scaled(1000), score));
        assertEquals(8000, score.min());
        assertEquals(8000, score.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInconsistencyOnDiagonal(CPSolver cp) {
        // (p, n) = (2, 2) is on the diagonal: score 0 < required minimum 1000
        CPIntVar p = makeIntVar(cp, 2, 2);
        CPIntVar n = makeIntVar(cp, 2, 2);
        CPIntVar score = makeIntVar(cp, 1000, 1000000);
        assertThrowsExactly(InconsistencyException.class,
                () -> cp.post(new ZDC(p, n, 4, 4, ZDCFunction.CHI2.scaled(1000), score)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBoundTighteningByBinarySearch(CPSolver cp) {
        // p fixed to all positives: the score decreases as n grows toward the diagonal,
        // so requiring a minimum score must decrease n.max
        int nTransP = 10, nTransN = 10;
        CPIntVar p = makeIntVar(cp, 10, 10);
        CPIntVar n = makeIntVar(cp, 0, 10);
        CPIntVar score = makeIntVar(cp, 5000, 1000000); // scaled chi2 >= 5.0
        cp.post(new ZDC(p, n, nTransP, nTransN, ZDCFunction.CHI2.scaled(1000), score));

        int nMax = n.max();
        assertTrue(nMax < 10, "n.max must be tightened");
        // the bound is sharp: n.max satisfies the score, n.max + 1 does not
        assertTrue(ZDCFunction.CHI2.scaled(1000).eval(10, nMax, nTransP, nTransN) >= 5000);
        assertTrue(ZDCFunction.CHI2.scaled(1000).eval(10, nMax + 1, nTransP, nTransN) < 5000);
    }

    /**
     * Brute-force map of the patterns whose scaled chi2 score reaches minScore,
     * pattern → truncated score.
     */
    static Map<String, Integer> bruteForceDiscriminative(TransactionDb db, int minScore) {
        TransactionDb dbP = db.subset(1);
        TransactionDb dbN = db.subset(0);
        Map<String, Integer> expected = new HashMap<>();
        int nItems = db.nItems() - 1;
        int[] items = new int[nItems];
        for (int mask = 0; mask < (1 << nItems); mask++) {
            int size = 0;
            for (int k = 0; k < nItems; k++) {
                if ((mask & (1 << k)) != 0)
                    items[size++] = k + 1;
            }
            int posSupport = MiningTestUtils.support(dbP, items, size);
            int negSupport = MiningTestUtils.support(dbN, items, size);
            int score = (int) ZDCFunction.CHI2.scaled(SCALE).eval(posSupport, negSupport, dbP.nTrans(), dbN.nTrans());
            if (score >= minScore)
                expected.put(MiningTestUtils.canonical(items, size), score);
        }
        return expected;
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testDiscriminativeEnumerationAgainstBruteForce(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 8; seed++) {
            Random rand = new Random(seed);
            TransactionDb db = MiningTestUtils.randomLabeledDb(rand, 2 + rand.nextInt(6), 2 + rand.nextInt(5), 0.5);
            TransactionDb dbP = db.subset(1);
            TransactionDb dbN = db.subset(0);

            // pick a threshold below the best score so that the query is non-trivial
            int best = bruteForceDiscriminative(db, 0).values().stream().max(Integer::compare).orElse(0);
            int minScore = Math.max(1, best / 2);
            Map<String, Integer> expected = bruteForceDiscriminative(db, minScore);

            CPSolver cp = solverSupplier.get();
            Map<String, Integer> sols = new HashMap<>();
            try {
                CPItemsetVar pattern = CPFactory.makeItemsetVar(cp, db);
                CPIntVar posSupport = makeIntVar(cp, 0, dbP.nTrans());
                CPIntVar negSupport = makeIntVar(cp, 0, dbN.nTrans());
                CPIntVar score = makeIntVar(cp, minScore, 1000000);
                cp.post(new ItemsetSupportIn(pattern, dbP, posSupport));
                cp.post(new ItemsetSupportIn(pattern, dbN, negSupport));
                cp.post(new ZDC(posSupport, negSupport, dbP.nTrans(), dbN.nTrans(),
                        ZDCFunction.CHI2.scaled(SCALE), score));

                int[] items = new int[pattern.nItems()];
                DFSearch search = CPFactory.makeDfs(cp, PatternSearches.requireExclude(pattern));
                search.onSolution(() -> {
                    assertTrue(score.isFixed(), "score must be fixed on a leaf");
                    int n = pattern.fillRequired(items);
                    String key = MiningTestUtils.canonical(items, n);
                    assertNull(sols.put(key, score.min()), "duplicate solution " + key);
                });
                search.solve();
            } catch (InconsistencyException e) {
                assertTrue(expected.isEmpty(), "seed " + seed);
                continue;
            }
            assertEquals(expected, sols, "seed " + seed);
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testClassSupportsAtLeaves(CPSolver cp) {
        TransactionDb db = MiningIO.readLabeledTransactionDb(LABELED_TEST);
        TransactionDb dbP = db.subset(1);
        TransactionDb dbN = db.subset(0);
        assertEquals(3, dbP.nTrans());
        assertEquals(2, dbN.nTrans());
        assertEquals(db.nItems(), dbP.nItems());

        CPItemsetVar pattern = CPFactory.makeItemsetVar(cp, db);
        CPIntVar posSupport = makeIntVar(cp, 0, dbP.nTrans());
        CPIntVar negSupport = makeIntVar(cp, 0, dbN.nTrans());
        cp.post(new ItemsetSupportIn(pattern, dbP, posSupport));
        cp.post(new ItemsetSupportIn(pattern, dbN, negSupport));

        int[] items = new int[pattern.nItems()];
        Set<String> seen = new HashSet<>();
        DFSearch search = CPFactory.makeDfs(cp, PatternSearches.requireExclude(pattern));
        search.onSolution(() -> {
            int n = pattern.fillRequired(items);
            seen.add(MiningTestUtils.canonical(items, n));
            assertTrue(posSupport.isFixed());
            assertTrue(negSupport.isFixed());
            assertEquals(MiningTestUtils.support(dbP, items, n), posSupport.min());
            assertEquals(MiningTestUtils.support(dbN, items, n), negSupport.min());
        });
        search.solve();
        assertEquals(1 << db.nItems(), seen.size()); // all patterns, nothing restricts the domain
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMaximizeScoreFindsOptimum(CPSolver cp) {
        TransactionDb db = MiningIO.readLabeledTransactionDb(LABELED_TEST);
        TransactionDb dbP = db.subset(1);
        TransactionDb dbN = db.subset(0);
        int expectedBest = bruteForceDiscriminative(db, 0).values().stream().max(Integer::compare).orElseThrow();

        CPItemsetVar pattern = CPFactory.makeItemsetVar(cp, db);
        CPIntVar posSupport = makeIntVar(cp, 0, dbP.nTrans());
        CPIntVar negSupport = makeIntVar(cp, 0, dbN.nTrans());
        CPIntVar score = makeIntVar(cp, 0, 1000000);
        cp.post(new ItemsetSupportIn(pattern, dbP, posSupport));
        cp.post(new ItemsetSupportIn(pattern, dbN, negSupport));
        cp.post(new ZDC(posSupport, negSupport, dbP.nTrans(), dbN.nTrans(),
                ZDCFunction.CHI2.scaled(SCALE), score));

        Objective obj = cp.maximize(score);
        int[] bestScore = new int[]{-1};
        DFSearch search = CPFactory.makeDfs(cp, PatternSearches.requireExclude(pattern));
        search.onSolution(() -> bestScore[0] = score.min());
        search.optimize(obj);

        assertEquals(expectedBest, bestScore[0]);
    }
}
