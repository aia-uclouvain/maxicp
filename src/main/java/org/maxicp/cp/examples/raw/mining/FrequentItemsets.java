/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.mining;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.patternvar.ClosedItemset;
import org.maxicp.cp.engine.constraints.patternvar.MinSupportItemset;
import org.maxicp.cp.engine.core.CPItemsetVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.MiningIO;
import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.search.DFSearch;
import org.maxicp.search.PatternSearches;
import org.maxicp.search.SearchStatistics;

import java.util.Arrays;

/**
 * Frequent (and optionally closed) itemset mining with an itemset pattern variable.
 * <p>
 * The unknown pattern is one first-class {@link CPItemsetVar}; minimum support
 * and closedness are independent constraints filtering its domain, and the
 * search branches on pattern refinements (require/exclude an item).
 * <p>
 * Usage: {@code FrequentItemsets [file] [minsup] [--closed]}
 */
public class FrequentItemsets {
    public static void main(String[] args) {
        String file = args.length > 0 ? args[0] : "data/MINING/ITEMSET/contextPasquier99.txt";
        int theta = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        boolean closed = Arrays.asList(args).contains("--closed");

        TransactionDb db = MiningIO.readTransactionDb(file);
        System.out.println(db + ", minsup=" + theta + (closed ? ", closed" : ""));

        long start = System.currentTimeMillis();
        CPSolver cp = CPFactory.makeSolver();
        CPItemsetVar pattern = CPFactory.makeItemsetVar(cp, db);

        cp.post(new MinSupportItemset(pattern, theta));
        if (closed) {
            cp.post(new ClosedItemset(pattern));
        }
        pattern.size().removeBelow(1); // non-empty patterns only
        cp.fixPoint();

        DFSearch search = CPFactory.makeDfs(cp, PatternSearches.minExtensionSupport(pattern));

        int[] items = new int[pattern.nItems()];
        search.onSolution(() -> {
            int n = pattern.fillRequired(items);
            int[] sorted = Arrays.copyOf(items, n);
            Arrays.sort(sorted);
            StringBuilder sb = new StringBuilder();
            for (int item : sorted) {
                sb.append(item).append(" ");
            }
            sb.append("#SUP: ").append(pattern.coverSize());
            System.out.println(sb);
        });

        SearchStatistics stats = search.solve();
        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);
        System.out.format("Time: %d ms\n", System.currentTimeMillis() - start);
    }
    /*
     * Expected output on contextPasquier99.txt with minsup=2 (matches Eclat):
     *   15 frequent non-empty itemsets, among which
     *   1 #SUP: 3, 1 2 3 5 #SUP: 2, 2 5 #SUP: 4, 3 #SUP: 4, ...
     * With --closed (matches LCM): 5 closed itemsets:
     *   3 #SUP: 4, 1 3 #SUP: 3, 2 5 #SUP: 4, 2 3 5 #SUP: 3, 1 2 3 5 #SUP: 2
     * On mushroom.txt (8124 transactions) with minsup=813 (10%) and --closed:
     *   4885 closed itemsets, ~100 ms search.
     */
}
