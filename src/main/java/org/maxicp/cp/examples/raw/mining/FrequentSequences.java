/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.mining;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.patternvar.MinSupportSeq;
import org.maxicp.cp.engine.constraints.patternvar.PatternLengthSeq;
import org.maxicp.cp.engine.core.CPSeqPatternVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.search.DFSearch;
import org.maxicp.search.PatternSearches;
import org.maxicp.search.SearchStatistics;

/**
 * Frequent sequential pattern mining with a sequence pattern variable.
 * <p>
 * The unknown pattern is one first-class {@link CPSeqPatternVar} grown by
 * prefix extension; its occurrence kernel is a PPIC-style pseudo-projected
 * database (Aoga, Guns, Schaus, ECML-PKDD 2016). Minimum support and length
 * constraints filter the candidate frontier, and the search branches on
 * append/exclude refinements.
 * <p>
 * Usage: {@code FrequentSequences [file.spmf] [minsup] [--verbose]}
 */
public class FrequentSequences {
    public static void main(String[] args) {
        String file = args.length > 0 ? args[0] : "data/MINING/SEQUENCE/test.spmf";
        SequenceDb db = SequenceDb.readSpmf(file);
        int theta = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        boolean verbose = args.length > 2 && args[2].equals("--verbose") || args.length == 0;
        System.out.println(db + ", minsup=" + theta);

        long start = System.currentTimeMillis();
        CPSolver cp = CPFactory.makeSolver();
        CPSeqPatternVar pattern = CPFactory.makeSeqPatternVar(cp, db);

        cp.post(new MinSupportSeq(pattern, theta));
        cp.post(new PatternLengthSeq(pattern, 1, db.maxSeqLength())); // non-empty patterns only

        DFSearch search = CPFactory.makeDfs(cp, PatternSearches.appendExclude(pattern));

        int[] symbols = new int[db.maxSeqLength()];
        search.onSolution(() -> {
            if (verbose) {
                int n = pattern.fillPrefix(symbols);
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < n; k++) {
                    sb.append(symbols[k]).append(" ");
                }
                sb.append("#SUP: ").append(pattern.support());
                System.out.println(sb);
            }
        });

        SearchStatistics stats = search.solve();
        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);
        System.out.format("Time: %d ms\n", System.currentTimeMillis() - start);
    }
    /*
     * Expected output on test.spmf with minsup=2: 27 frequent non-empty
     * sequences (matches PrefixSpan, see data/MINING/SEQUENCE/oracle/).
     * On FIFA.spmf (20450 sequences, alphabet 2990) with minsup=4090 (20%):
     * 938 frequent sequences (matches PrefixSpan reference).
     */
}
