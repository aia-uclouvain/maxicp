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
import org.maxicp.search.PatternSearches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.engine.constraints.patternvar.MinSupportItemsetTest.enumerate;

public class ClosedItemsetTest extends CPSolverTest {

    static final String PASQUIER = "data/MINING/ITEMSET/contextPasquier99.txt";
    static final String PASQUIER_LCM_2 = "data/MINING/ITEMSET/oracle/contextPasquier99-lcm-2.txt";

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAgainstLcmReferenceFile(CPSolver cp) {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, db);
        cp.post(new MinSupportItemset(p, 2));
        cp.post(new ClosedItemset(p));

        Map<String, Integer> sols = enumerate(cp, p, PatternSearches.requireExclude(p));

        // the empty pattern is closed here: no item appears in all transactions
        assertEquals(5, sols.remove(""));
        assertEquals(MiningTestUtils.readSols(PASQUIER_LCM_2), sols);
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testAgainstBruteForceOnRandomDatabases(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 10; seed++) {
            Random rand = new Random(seed);
            TransactionDb db = MiningTestUtils.randomDb(rand, 1 + rand.nextInt(8), 2 + rand.nextInt(6), 0.5);
            int theta = 1 + rand.nextInt(3);
            Map<String, Integer> expected = MiningTestUtils.closedFrequentItemsets(db, theta);

            CPSolver cp = solverSupplier.get();
            CPItemsetVar p = CPFactory.makeItemsetVar(cp, db);
            try {
                cp.post(new MinSupportItemset(p, theta));
                cp.post(new ClosedItemset(p));
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
    public void testClosureExtension(CPSolver cp) {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, db);
        cp.post(new MinSupportItemset(p, 2));
        cp.post(new ClosedItemset(p));

        // every transaction containing 1 also contains 3: requiring 1 must force 3
        p.require(1);
        cp.fixPoint();
        assertTrue(p.isRequired(3));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSubsumptionByExcludedItem(CPSolver cp) {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        CPItemsetVar p = CPFactory.makeItemsetVar(cp, db);
        cp.post(new MinSupportItemset(p, 2));
        cp.post(new ClosedItemset(p));

        // excluding 3 then requiring 1 leaves cover(R) ⊆ cover({3}): no closed completion
        p.exclude(3);
        cp.fixPoint();
        assertThrowsExactly(InconsistencyException.class, () -> {
            p.require(1);
            cp.fixPoint();
        });
    }
}
