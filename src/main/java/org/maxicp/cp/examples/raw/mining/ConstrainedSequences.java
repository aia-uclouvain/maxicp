/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.mining;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.patternvar.MinSupportSeq;
import org.maxicp.cp.engine.constraints.patternvar.PatternLengthSeq;
import org.maxicp.cp.engine.constraints.patternvar.PrefixAutomaton;
import org.maxicp.cp.engine.core.CPSeqPatternVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.Dfa;
import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.search.DFSearch;
import org.maxicp.search.PatternSearches;
import org.maxicp.search.SearchStatistics;

/**
 * Constrained sequential pattern mining: a combined query where minimum support,
 * a regular-language restriction (the pattern must contain a given symbol) and a
 * length window all filter the refinement frontier of one sequence pattern
 * variable — the compositional query motivating the pattern-variable design.
 * <p>
 * Usage: {@code ConstrainedSequences [file.spmf] [minsup] [symbol] [maxLength]}
 */
public class ConstrainedSequences {
    public static void main(String[] args) {
        String file = args.length > 0 ? args[0] : "data/MINING/SEQUENCE/test.spmf";
        SequenceDb db = SequenceDb.readSpmf(file);
        int theta = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        int symbol = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        int maxLength = args.length > 3 ? Integer.parseInt(args[3]) : db.maxSeqLength();
        System.out.println(db + ", minsup=" + theta + ", contains " + symbol + ", length in [1," + maxLength + "]");

        // 2-state automaton accepting the words that contain the symbol
        int[][] transitions = new int[2][db.nSymbols()];
        for (int a = 1; a < db.nSymbols(); a++) {
            transitions[0][a] = a == symbol ? 1 : 0;
            transitions[1][a] = 1;
        }
        Dfa containsSymbol = new Dfa(2, 0, transitions, 1);

        long start = System.currentTimeMillis();
        CPSolver cp = CPFactory.makeSolver();
        CPSeqPatternVar pattern = CPFactory.makeSeqPatternVar(cp, db);

        cp.post(new MinSupportSeq(pattern, theta));
        cp.post(new PrefixAutomaton(pattern, containsSymbol));
        cp.post(new PatternLengthSeq(pattern, 1, maxLength));

        DFSearch search = CPFactory.makeDfs(cp, PatternSearches.appendExclude(pattern));

        int[] symbols = new int[db.maxSeqLength()];
        search.onSolution(() -> {
            int n = pattern.fillPrefix(symbols);
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < n; k++) {
                sb.append(symbols[k]).append(" ");
            }
            sb.append("#SUP: ").append(pattern.support());
            System.out.println(sb);
        });

        SearchStatistics stats = search.solve();
        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);
        System.out.format("Time: %d ms\n", System.currentTimeMillis() - start);
    }
    /*
     * Expected output on test.spmf with minsup=2, contains 3, maxLength unrestricted:
     * the 14 frequent sequences containing symbol 3 (subset of the 27 frequent ones).
     * On FIFA.spmf with minsup=4090 (20%), contains 30, maxLength 3:
     * 73 sequences, ~1.2 s (the automaton and length window filter the frontier
     * of the same variable the support constraint projects).
     */
}
