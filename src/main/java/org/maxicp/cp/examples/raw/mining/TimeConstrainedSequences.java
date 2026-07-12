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
import org.maxicp.cp.engine.core.mining.TimeOption;
import org.maxicp.search.DFSearch;
import org.maxicp.search.PatternSearches;
import org.maxicp.search.SearchStatistics;

/**
 * Time-constrained sequential pattern mining with a sequence pattern variable.
 * <p>
 * The unknown pattern is one first-class {@link CPSeqPatternVar} over a timed
 * sequence database; its occurrence kernel is the PPICt embedding-window
 * pseudo-projection (Aoga, Guns, Schaus, Mining Time-constrained Sequential
 * Patterns with Constraint Programming, Constraints 22:548-570, 2017).
 * An occurrence must respect gap windows between consecutive matched events
 * and a span window over the whole match (see {@link TimeOption}); the
 * non-anti-monotone minimum span only restricts where the pattern may
 * terminate, through the complete support.
 * <p>
 * Usage: {@code TimeConstrainedSequences [file.spmf] [minsup] [minspan maxspan mingap maxgap]}
 */
public class TimeConstrainedSequences {
    public static void main(String[] args) {
        String file = args.length > 0 ? args[0] : "data/MINING/SEQUENCE/test.postime.spmf";
        SequenceDb db = SequenceDb.readSpmfWithTime(file);
        int theta = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        // the Hirate-Yamana benchmark restrictions: span in [0,2], gap in [0,2]
        TimeOption time = args.length > 5
                ? new TimeOption(Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                Integer.parseInt(args[4]), Integer.parseInt(args[5]))
                : new TimeOption(0, 2, 0, 2);
        System.out.println(db + ", minsup=" + theta + ", " + time);

        long start = System.currentTimeMillis();
        CPSolver cp = CPFactory.makeSolver();
        CPSeqPatternVar pattern = CPFactory.makeSeqPatternVar(cp, db, time);

        cp.post(new MinSupportSeq(pattern, theta));
        cp.post(new PatternLengthSeq(pattern, 1, db.maxSeqLength())); // non-empty patterns only

        DFSearch search = CPFactory.makeDfs(cp, PatternSearches.appendExclude(pattern));

        int[] symbols = new int[db.maxSeqLength()];
        search.onSolution(() -> {
            int n = pattern.fillPrefix(symbols);
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < n; k++) {
                sb.append(symbols[k]).append(" ");
            }
            sb.append("#SUP: ").append(pattern.completeSupport());
            System.out.println(sb);
        });

        SearchStatistics stats = search.solve();
        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);
        System.out.format("Time: %d ms\n", System.currentTimeMillis() - start);
    }
    /*
     * Expected output on test.postime.spmf with minsup=2 and TimeOption(0, 2, 0, 2)
     * (the Hirate-Yamana reference query, spmf test_HirateYamana_2_02_02.txt),
     * 13 time-constrained frequent sequences:
     * 1 2 4 #SUP: 2
     * 1 2 #SUP: 3
     * 1 4 #SUP: 3
     * 1 3 #SUP: 2
     * 1 #SUP: 4
     * 2 3 #SUP: 2
     * 2 4 #SUP: 2
     * 2 #SUP: 4
     * 3 2 #SUP: 3
     * 3 #SUP: 4
     * 4 2 #SUP: 2
     * 4 3 #SUP: 2
     * 4 #SUP: 3
     * #Solutions: 13
     * (solution order depends on the search; supports are sequence supports, so
     * "3 2" has support 3 where spmf reports its two interval-annotated variants
     * with support 2 each).
     * With args "data/MINING/SEQUENCE/test.postime.spmf 2 3 3 0 3" (span exactly
     * 3): the 4 solutions 1 3 #SUP: 2 / 1 4 3 #SUP: 2 / 2 4 2 #SUP: 2 /
     * 2 2 #SUP: 2 — patterns may only terminate once the minimum span is
     * reached (completeSupport >= minsup).
     */
}
