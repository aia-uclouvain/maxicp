/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPItemsetVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.MiningIO;
import org.maxicp.cp.engine.core.mining.MiningTestUtils;
import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.search.DFSearch;
import org.maxicp.search.PatternSearches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * Tests of the NV-GC CoverSize baseline, including the three-level differential
 * comparison STD (decomposition) / NV-GC (CoverSize) / PV (pattern variable).
 */
public class CoverSizeTest extends CPSolverTest {

    static final String PASQUIER = "data/MINING/ITEMSET/contextPasquier99.txt";
    static final String PASQUIER_ECLAT_2 = "data/MINING/ITEMSET/oracle/contextPasquier99-eclat-2.txt";

    static Supplier<Runnable[]> staticBoolBranching(CPSolver cp, CPBoolVar[] xs) {
        return () -> {
            CPBoolVar x = null;
            for (CPBoolVar candidate : xs) {
                if (!candidate.isFixed()) {
                    x = candidate;
                    break;
                }
            }
            if (x == null)
                return EMPTY;
            CPBoolVar selected = x;
            return branch(
                    () -> {
                        selected.fix(true);
                        cp.fixPoint();
                    },
                    () -> {
                        selected.fix(false);
                        cp.fixPoint();
                    });
        };
    }

    /**
     * Enumerates a Boolean-item model and returns the map pattern → support.
     */
    static Map<String, Integer> enumerateBoolModel(CPSolver cp, CPBoolVar[] items, CPIntVar support) {
        Map<String, Integer> sols = new HashMap<>();
        int[] buffer = new int[items.length];
        DFSearch search = CPFactory.makeDfs(cp, staticBoolBranching(cp, items));
        search.onSolution(() -> {
            assertTrue(support.isFixed(), "support must be fixed on a leaf");
            int n = 0;
            for (int i = 0; i < items.length; i++) {
                if (items[i].isTrue())
                    buffer[n++] = i;
            }
            String pattern = MiningTestUtils.canonical(buffer, n);
            assertNull(sols.put(pattern, support.min()), "duplicate solution " + pattern);
        });
        search.solve();
        return sols;
    }

    /**
     * Builds the STD decomposed model: one reified coverage Boolean per transaction
     * (a transaction is covered iff no absent item is selected) and a sum for the support.
     */
    static CPIntVar stdSupport(CPSolver cp, CPBoolVar[] items, TransactionDb db) {
        CPBoolVar[] covered = new CPBoolVar[db.nTrans()];
        for (int t = 0; t < db.nTrans(); t++) {
            boolean[] present = new boolean[db.nItems()];
            for (int item : db.transaction(t)) {
                present[item] = true;
            }
            int nAbsent = 0;
            for (int i = 0; i < db.nItems(); i++) {
                if (!present[i])
                    nAbsent++;
            }
            CPBoolVar[] absent = new CPBoolVar[nAbsent];
            int k = 0;
            for (int i = 0; i < db.nItems(); i++) {
                if (!present[i])
                    absent[k++] = items[i];
            }
            covered[t] = not(isOr(absent));
        }
        return sum(covered);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAgainstEclatReferenceFile(CPSolver cp) {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        CPBoolVar[] items = makeBoolVarArray(cp, db.nItems());
        CPIntVar support = makeIntVar(cp, 0, db.nTrans());
        cp.post(new CoverSize(items, support, db));
        cp.post(ge(support, 2));

        Map<String, Integer> sols = enumerateBoolModel(cp, items, support);

        assertEquals(5, sols.remove(""));
        assertEquals(MiningTestUtils.readSols(PASQUIER_ECLAT_2), sols);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRootFiltering(CPSolver cp) {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        CPBoolVar[] items = makeBoolVarArray(cp, db.nItems());
        CPIntVar support = makeIntVar(cp, 0, db.nTrans());
        cp.post(new CoverSize(items, support, db));
        cp.post(ge(support, 2));
        // items 0 (support 0) and 4 (support 1) are infrequent
        assertTrue(items[0].isFalse());
        assertTrue(items[4].isFalse());
        assertFalse(items[1].isFixed());
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testThreeLevelsAgainstBruteForce(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 8; seed++) {
            Random rand = new Random(seed);
            TransactionDb db = MiningTestUtils.randomDb(rand, 1 + rand.nextInt(8), 2 + rand.nextInt(6), 0.5);
            int theta = 1 + rand.nextInt(3);
            Map<String, Integer> expected = MiningTestUtils.frequentItemsets(db, theta);

            // PV: pattern variable with its support view
            Map<String, Integer> pvSols = null;
            CPSolver cp = solverSupplier.get();
            try {
                CPItemsetVar p = CPFactory.makeItemsetVar(cp, db);
                cp.post(ge(p.support(), theta));
                pvSols = MinSupportItemsetTest.enumerate(cp, p, PatternSearches.requireExclude(p));
            } catch (InconsistencyException e) {
                assertTrue(expected.isEmpty(), "seed " + seed);
            }

            // NV-GC: Boolean items + CoverSize global constraint
            Map<String, Integer> nvgcSols = null;
            cp = solverSupplier.get();
            try {
                CPBoolVar[] items = makeBoolVarArray(cp, db.nItems());
                CPIntVar support = makeIntVar(cp, 0, db.nTrans());
                cp.post(new CoverSize(items, support, db));
                cp.post(ge(support, theta));
                nvgcSols = enumerateBoolModel(cp, items, support);
            } catch (InconsistencyException e) {
                assertTrue(expected.isEmpty(), "seed " + seed);
            }

            // STD: Boolean items + reified transaction coverage + sum
            Map<String, Integer> stdSols = null;
            cp = solverSupplier.get();
            try {
                CPBoolVar[] items = makeBoolVarArray(cp, db.nItems());
                CPIntVar support = stdSupport(cp, items, db);
                cp.post(ge(support, theta));
                stdSols = enumerateBoolModel(cp, items, support);
            } catch (InconsistencyException e) {
                assertTrue(expected.isEmpty(), "seed " + seed);
            }

            if (pvSols != null)
                assertEquals(expected, pvSols, "PV differs, seed " + seed);
            if (nvgcSols != null)
                assertEquals(expected, nvgcSols, "NV-GC differs, seed " + seed);
            if (stdSols != null)
                assertEquals(expected, stdSols, "STD differs, seed " + seed);
        }
    }
}
