/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core.mining;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransactionDbTest {

    static final String PASQUIER = "data/MINING/ITEMSET/contextPasquier99.txt";
    static final String PASQUIER_ECLAT_2 = "data/MINING/ITEMSET/oracle/contextPasquier99-eclat-2.txt";

    @Test
    public void testReadContextPasquier99() {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        assertEquals(5, db.nTrans());
        assertEquals(6, db.nItems()); // items 1..5, index 0 unused
        assertArrayEquals(new int[]{1, 3, 4}, db.transaction(0));
        assertArrayEquals(new int[]{2, 3, 5}, db.transaction(1));
        assertArrayEquals(new int[]{1, 2, 3, 5}, db.transaction(2));
        assertArrayEquals(new int[]{2, 5}, db.transaction(3));
        assertArrayEquals(new int[]{1, 2, 3, 5}, db.transaction(4));
        assertEquals(0, db.itemSupport(0));
        assertEquals(3, db.itemSupport(1));
        assertEquals(4, db.itemSupport(2));
        assertEquals(4, db.itemSupport(3));
        assertEquals(1, db.itemSupport(4));
        assertEquals(4, db.itemSupport(5));
    }

    @Test
    public void testNormalization() {
        TransactionDb db = new TransactionDb("t", new int[][]{{3, 1, 3, 2}, {}});
        assertEquals(2, db.nTrans());
        assertEquals(4, db.nItems());
        assertArrayEquals(new int[]{1, 2, 3}, db.transaction(0));
        assertArrayEquals(new int[]{}, db.transaction(1));
        assertEquals(1, db.itemSupport(3));
    }

    @Test
    public void testReadSolsOracle() {
        Map<String, Integer> sols = MiningTestUtils.readSols(PASQUIER_ECLAT_2);
        assertEquals(15, sols.size());
        assertEquals(3, sols.get("1"));
        assertEquals(2, sols.get("1 2 3 5"));
        assertEquals(3, sols.get("2 3"));
    }

    @Test
    public void testBruteForceMatchesEclatReference() {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        Map<String, Integer> bruteForce = MiningTestUtils.frequentItemsets(db, 2);
        Map<String, Integer> reference = MiningTestUtils.readSols(PASQUIER_ECLAT_2);
        // brute force additionally contains the empty pattern (support 5)
        assertEquals(5, bruteForce.remove(""));
        assertEquals(reference, bruteForce);
    }

    @Test
    public void testReadLabeled() {
        TransactionDb db = MiningIO.readLabeledTransactionDb("data/MINING/ITEMSET/withlabels/test.txt");
        assertEquals(5, db.nTrans());
        assertEquals(5, db.nItems());
        assertEquals(1, db.label(0));
        assertEquals(0, db.label(1));
        assertArrayEquals(new int[]{1, 2, 4}, db.transaction(0));

        TransactionDb pos = db.subset(1);
        TransactionDb neg = db.subset(0);
        assertEquals(3, pos.nTrans());
        assertEquals(2, neg.nTrans());
        // the item universe is preserved in class subsets
        assertEquals(db.nItems(), pos.nItems());
        assertEquals(db.nItems(), neg.nItems());
        assertArrayEquals(new int[]{2, 3, 4}, neg.transaction(0));
    }

    @Test
    public void testBruteForceClosedMatchesLcmReference() {
        TransactionDb db = MiningIO.readTransactionDb(PASQUIER);
        Map<String, Integer> bruteForce = MiningTestUtils.closedFrequentItemsets(db, 2);
        Map<String, Integer> reference = MiningTestUtils.readSols("data/MINING/ITEMSET/oracle/contextPasquier99-lcm-2.txt");
        // the empty pattern is closed here: no item appears in all transactions
        assertEquals(5, bruteForce.remove(""));
        assertEquals(reference, bruteForce);
    }
}
