/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.nqueens;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.AllDifferentFWC;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSTreeRecorder;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;


/**
 * The N-Queens problem.
 * <a href="http://csplib.org/Problems/prob054/">CSPLib</a>.
 */
public class NQueensSearchTree {
    public static void main(String[] args) {
        int n = 15;

        CPSolver cp = CPFactory.makeSolver();
        CPIntVar[] q = CPFactory.makeIntVarArray(cp, n, n);
        CPIntVar[] qL = CPFactory.makeIntVarArray(n, i -> minus(q[i],i));
        CPIntVar[] qR = CPFactory.makeIntVarArray(n, i -> plus(q[i],i));

        cp.post(new AllDifferentFWC(q));
        cp.post(new AllDifferentFWC(qL));
        cp.post(new AllDifferentFWC(qR));

        // DFSearch search = makeDfs(cp, staticOrder(q));
        //DFSearch search = makeDfs(cp, conflictOrderingSearch(minDomVariableSelector(q), x -> x.min()));
        //DFSearch search = makeDfs(cp, firstFail(q));

        // DFSearch search = makeDfs(cp, firstFailNary(q));
        DFSearch search = makeDfs(cp, staticOrderNary(q));



        DFSTreeRecorder treeRecorder = new DFSTreeRecorder();
        search.setDFSListener(treeRecorder);

        search.onSolution(() ->
                System.out.println("solution:" + Arrays.toString(q))
        );
        SearchStatistics stats = search.solve(statistics -> statistics.numberOfSolutions() == 1);

        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);

        treeRecorder.toTikz(0.2, 0.4, 0.2, 1.5);

    }
}
