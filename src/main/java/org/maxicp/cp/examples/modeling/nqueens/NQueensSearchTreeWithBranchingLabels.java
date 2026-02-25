/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling.nqueens;


import org.maxicp.ModelDispatcher;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSTreeRecorder;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

/**
 * The N-Queens problem.
 * <a href="http://csplib.org/Problems/prob054/">CSPLib</a>.
 */
public class NQueensSearchTreeWithBranchingLabels {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        int n = 6;

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
            int parentId = currNodeId.get();
            Integer i = selectMin(
                    IntStream.range(0, n).boxed().toList(),
                    qi -> q[qi].size() > 1,
                    qi -> q[qi].size()
            );
            if (i == null) {
                treeRecorder.solution(currNodeId.incrementAndGet(),parentId);
                return EMPTY;
            }
            else {
                int v = q[i].min();
                Runnable l = () -> {
                    String label = String.format("$q_%d = %d$",i,v);
                    try {
                        model.add(eq(q[i], v));
                        treeRecorder.branch(currNodeId.incrementAndGet(),parentId,label);
                    } catch (InconsistencyException e) {
                        treeRecorder.fail(currNodeId.incrementAndGet(),parentId,label);
                        throw e;
                    }
                };
                Runnable r = () -> {
                    String label = String.format("$q_%d \\neq %d$",i,v);
                    try {
                        model.add(neq(q[i], v));
                        treeRecorder.branch(currNodeId.incrementAndGet(),parentId,"");
                    } catch (InconsistencyException e) {
                        treeRecorder.fail(currNodeId.incrementAndGet(),parentId,"");
                        throw e;
                    }
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

        treeRecorder.toTikz(1.5,0.4,0.2, 1.5);

        System.out.println(stats);

    }
}