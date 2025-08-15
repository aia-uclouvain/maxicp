/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;


import org.maxicp.ModelDispatcher;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSTreeRecorder;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

/**
 * The N-Queens problem.
 * <a href="http://csplib.org/Problems/prob054/">CSPLib</a>.
 */
public class NQueensSearchTreeWithBranchingLabels {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        int n = 7;

        ModelDispatcher model = makeModelDispatcher();

        IntVar[] q = model.intVarArray(n, n);
        IntExpression[] qL = model.intVarArray(n,i -> q[i].plus(i));
        IntExpression[] qR = model.intVarArray(n,i -> q[i].minus(i));

        model.add(allDifferent(q));
        model.add(allDifferent(qL));
        model.add(allDifferent(qR));

        DFSTreeRecorder treeRecorder = new DFSTreeRecorder();
        AtomicInteger currNodeId = new AtomicInteger(treeRecorder.root);

        Supplier<Runnable[]> branching = () -> {
            IntExpression qs = selectMin(q,
                    qi -> qi.size() > 1,
                    qi -> qi.size());
            if (qs == null)
                return EMPTY;
            else {
                int parentId = currNodeId.get();
                int v = qs.min();
                Runnable l = () -> {
                    model.add(eq(qs, v));
                    treeRecorder.branch(currNodeId.incrementAndGet(),parentId);
                };
                Runnable r = () -> {
                    model.add(neq(qs, v));
                    treeRecorder.branch(currNodeId.incrementAndGet(),parentId);
                };
                return branch(l,r);
            }
        };

        ConcreteCPModel cp = model.cpInstantiate();
        DFSearch dfs = cp.dfSearch(branching);
        dfs.onSolution(() -> {
            System.out.println(Arrays.toString(q));
        });

        SearchStatistics stats = dfs.solve();

        treeRecorder.toTikz(0.2,0.4,0.2, 1.5);

        System.out.println(stats);

    }
}