/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;


import org.maxicp.ModelDispatcher;
import org.maxicp.cp.modeling.CPModelInstantiator;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.Factory;
import static org.maxicp.modeling.Factory.*;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.Model;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.modeling.algebra.bool.NotEq;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.AllDifferent;
import org.maxicp.modeling.symbolic.SymbolicModel;
import org.maxicp.search.*;
import static org.maxicp.search.Searches.*;

import org.maxicp.util.TimeIt;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;


import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * The N-Queens problem.
 * <a href="http://csplib.org/Problems/prob054/">CSPLib</a>.
 */
public class NQueens {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        int n = 12;

        ModelDispatcher model = makeModelDispatcher();

        IntVar[] q = model.intVarArray(n, n);
        IntExpression[] qL = model.intVarArray(n,i -> q[i].plus(i));
        IntExpression[] qR = model.intVarArray(n,i -> q[i].minus(i));

        model.add(allDifferent(q));
        model.add(allDifferent(qL));
        model.add(allDifferent(qR));

        Supplier<Runnable[]> branching = () -> {
            IntExpression qs = selectMin(q,
                    qi -> qi.size() > 1,
                    qi -> qi.size());
            if (qs == null)
                return EMPTY;
            else {
                int v = qs.min();
                return branch(() -> model.add(eq(qs, v)), () -> model.add(neq(qs, v)));
            }
        };

        ConcreteCPModel cp = model.cpInstantiate();
        DFSearch dfs = cp.dfSearch(branching);
        dfs.onSolution(() -> {
            System.out.println(Arrays.toString(q));
        });
        SearchStatistics stats = dfs.solve();
        System.out.println(stats);

    }
}