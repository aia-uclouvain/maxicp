/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.MiningIO;
import org.maxicp.cp.engine.core.mining.MiningTestUtils;
import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.cp.engine.constraints.patternvar.CoverSizeTest.enumerateBoolModel;

/**
 * Tests of the two native-variable closed-mining baselines (fused ClosedFimNv
 * and duplicated-state CoverSize+ClosedNv) against the LCM reference and the
 * brute-force oracle.
 */
public class ClosedNvTest extends CPSolverTest {

    static final String PASQUIER = "data/MINING/ITEMSET/contextPasquier99.txt";
    static final String PASQUIER_LCM_2 = "data/MINING/ITEMSET/oracle/contextPasquier99-lcm-2.txt";

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFusedAgainstLcmReferenceFile(CPSolver cp) {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        CPBoolVar[] items = makeBoolVarArray(cp, db.nItems());
        CPIntVar support = makeIntVar(cp, 0, db.nTrans());
        cp.post(new CoverSize(items, support, db)); // support view for the solution collector
        cp.post(new ClosedFimNv(items, 2, db));

        Map<String, Integer> sols = enumerateBoolModel(cp, items, support);
        assertEquals(5, sols.remove(""));
        assertEquals(MiningTestUtils.readSols(PASQUIER_LCM_2), sols);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDuplicatedAgainstLcmReferenceFile(CPSolver cp) {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        CPBoolVar[] items = makeBoolVarArray(cp, db.nItems());
        CPIntVar support = makeIntVar(cp, 0, db.nTrans());
        cp.post(new CoverSize(items, support, db));
        cp.post(ge(support, 2));
        cp.post(new ClosedNv(items, db));

        Map<String, Integer> sols = enumerateBoolModel(cp, items, support);
        assertEquals(5, sols.remove(""));
        assertEquals(MiningTestUtils.readSols(PASQUIER_LCM_2), sols);
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testBothArchitecturesAgainstBruteForce(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 8; seed++) {
            Random rand = new Random(seed);
            TransactionDb db = MiningTestUtils.randomDb(rand, 1 + rand.nextInt(8), 2 + rand.nextInt(6), 0.5);
            int theta = 1 + rand.nextInt(3);
            Map<String, Integer> expected = MiningTestUtils.closedFrequentItemsets(db, theta);

            // fused single propagator
            CPSolver cp = solverSupplier.get();
            try {
                CPBoolVar[] items = makeBoolVarArray(cp, db.nItems());
                CPIntVar support = makeIntVar(cp, 0, db.nTrans());
                cp.post(new CoverSize(items, support, db));
                cp.post(new ClosedFimNv(items, theta, db));
                assertEquals(expected, enumerateBoolModel(cp, items, support), "fused, seed " + seed);
            } catch (InconsistencyException e) {
                assertTrue(expected.isEmpty(), "fused, seed " + seed);
            }

            // duplicated-state pair of propagators
            cp = solverSupplier.get();
            try {
                CPBoolVar[] items = makeBoolVarArray(cp, db.nItems());
                CPIntVar support = makeIntVar(cp, 0, db.nTrans());
                cp.post(new CoverSize(items, support, db));
                cp.post(ge(support, theta));
                cp.post(new ClosedNv(items, db));
                assertEquals(expected, enumerateBoolModel(cp, items, support), "duplicated, seed " + seed);
            } catch (InconsistencyException e) {
                assertTrue(expected.isEmpty(), "duplicated, seed " + seed);
            }
        }
    }
}
