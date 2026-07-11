/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.mining;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.patternvar.ItemsetSupportIn;
import org.maxicp.cp.engine.constraints.patternvar.ZDC;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPItemsetVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.MiningIO;
import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.cp.engine.core.mining.ZDCFunction;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.PatternSearches;
import org.maxicp.search.SearchStatistics;

import java.util.Arrays;

/**
 * Discriminative itemset mining: find the pattern that best separates the
 * positive from the negative transactions of a labeled database, by maximizing
 * a Zero-Diagonal-Convex score (information gain) over its class supports.
 * <p>
 * The model uses one pattern variable, two class-support channelings sharing
 * its domain, and the {@link ZDC} scoring constraint — the discriminative query
 * is a composition of independent constraints over one first-class pattern.
 * <p>
 * Usage: {@code DiscriminativeItemsets [labeledFile]}
 */
public class DiscriminativeItemsets {
    public static void main(String[] args) {
        String file = args.length > 0 ? args[0] : "data/MINING/ITEMSET/withlabels/test.txt";
        double scale = 100000;

        TransactionDb db = MiningIO.readLabeledTransactionDb(file);
        TransactionDb dbPos = db.subset(1);
        TransactionDb dbNeg = db.subset(0);
        System.out.println(db + ", positives=" + dbPos.nTrans() + ", negatives=" + dbNeg.nTrans());

        long start = System.currentTimeMillis();
        CPSolver cp = CPFactory.makeSolver();
        CPItemsetVar pattern = CPFactory.makeItemsetVar(cp, db);
        CPIntVar posSupport = CPFactory.makeIntVar(cp, 0, dbPos.nTrans());
        CPIntVar negSupport = CPFactory.makeIntVar(cp, 0, dbNeg.nTrans());
        CPIntVar score = CPFactory.makeIntVar(cp, 0, 1000000);

        cp.post(new ItemsetSupportIn(pattern, dbPos, posSupport));
        cp.post(new ItemsetSupportIn(pattern, dbNeg, negSupport));
        cp.post(new ZDC(posSupport, negSupport, dbPos.nTrans(), dbNeg.nTrans(),
                ZDCFunction.INF_GAIN.scaled(scale), score));

        Objective obj = cp.maximize(score);
        DFSearch search = CPFactory.makeDfs(cp, PatternSearches.minExtensionSupport(pattern));

        int[] items = new int[pattern.nItems()];
        search.onSolution(() -> {
            int n = pattern.fillRequired(items);
            int[] sorted = Arrays.copyOf(items, n);
            Arrays.sort(sorted);
            System.out.println("pattern " + Arrays.toString(sorted)
                    + " pos:" + posSupport + "/" + dbPos.nTrans()
                    + " neg:" + negSupport + "/" + dbNeg.nTrans()
                    + " score:" + (score.min() / scale));
        });

        SearchStatistics stats = search.optimize(obj);
        System.out.format("Statistics: %s\n", stats);
        System.out.format("Time: %d ms\n", System.currentTimeMillis() - start);
    }
    /*
     * Expected output on withlabels/test.txt (3 positives, 2 negatives):
     * the best InfGain pattern is [1, 2, 4] (covers all positives, no negative),
     * score 0.67301 (entropy of the 3/2 class split, natural log).
     */
}
