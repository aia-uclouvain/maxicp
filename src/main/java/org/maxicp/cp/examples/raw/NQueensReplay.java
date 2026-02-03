/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSLinearizer;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.EMPTY;


/**
 * The N-Queens problem.
 * <a href="http://csplib.org/Problems/prob054/">CSPLib</a>.
 */
public class NQueensReplay {
    public static void main(String[] args) {
        int n = 13;

        CPSolver cp = CPFactory.makeSolver();
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, n, n);
        CPIntVar[] qL = CPFactory.makeIntVarArray(n, i -> minus(q[i],i));
        CPIntVar[] qR = CPFactory.makeIntVarArray(n, i -> plus(q[i],i));

        cp.post(allDifferent(q));
        cp.post(allDifferent(qL));
        cp.post(allDifferent(qR));


        DFSLinearizer linearizer = new DFSLinearizer();

        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFail(q));

        SearchStatistics stats0 = search.solve();

        System.out.format("Original Statistics: %s\n", stats0);

        SearchStatistics stats1 = search.solve(linearizer);

        System.out.format("Statistics Replay: %s\n", stats1);

        SearchStatistics stats2 = search.replaySubjectTo(linearizer, q, () -> {
            cp.post(allDifferentDC(q));
            cp.post(allDifferentDC(qL));
            cp.post(allDifferentDC(qR));
        });
        System.out.println("Statistics Replay with DC AllDiff: " + stats2);



    }
}
