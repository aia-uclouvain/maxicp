/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;


import org.maxicp.ModelDispatcher;
import org.maxicp.cp.modeling.CPModelInstantiator;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.Model;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.SymbolicModel;
import org.maxicp.search.*;
import org.maxicp.util.TimeIt;

import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

/**
 * The N-Queens problem solved with Embarassingly Parallel Search (EPS).
 * <a href="http://csplib.org/Problems/prob054/">CSPLib</a>.
 */
public class NQueensEPS {
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

        // Solve with standard Search
        System.out.println("--- SIMPLE SOLVING");
        long time = TimeIt.run(() -> {
            model.runCP((cp) -> {
                DFSearch search = cp.dfSearch(branching);
                System.out.println("Total number of solutions: " + search.solve().numberOfSolutions());
            });
        });
        System.out.println("Time taken for simple resolution: " + (time/1000000000.));

        // Solve with Embarassingly Parallel Search (EPS)
        System.out.println("--- EPS (DFS for decomposition)");
        long time2 = TimeIt.run(() -> {

            ExecutorService executorService = Executors.newFixedThreadPool(8);
            Function<Model, SearchStatistics> epsSolve = (m) -> {
                return model.runAsConcrete(CPModelInstantiator.withTrailing, m, (cp) -> {
                    DFSearch search = cp.dfSearch(branching);
                    return search.solve();
                });
            };

            // Collect sub-problems by collecting leaf nodes in a depth limited search
            // and solve them in parallel
            LinkedList<Future<SearchStatistics>> results = new LinkedList<>();
            model.runCP((cp) -> {
                DFSearch search = cp.dfSearch(new LimitedDepthBranching(branching, 10));
                search.onSolution(() -> {
                    // get the sub-problem in this node
                    Model m = cp.symbolicCopy();
                    // and solve it
                    results.add(executorService.submit(() -> epsSolve.apply(m)));
                });
                System.out.println("Number of EPS subproblems generated: " + search.solve().numberOfSolutions());
            });

            int count = 0;
            for (var fr : results) {
                try {
                    count += fr.get().numberOfSolutions();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Total number of solutions (in EPS): " + count);
            executorService.shutdown();
        });
        System.out.println("Time taken for EPS resolution: " + (time2/1000000000.));

        //
        // EPS with bigger-cartesian-product-first decomposition solving demo
        //
        System.out.println("--- EPS (BestFirstSearch based on Cartesian Space for decomposition)");
        long time3 = TimeIt.run(() -> {
            ExecutorService executorService = Executors.newFixedThreadPool(8);

            Function<Model, SearchStatistics> epsSolve = (m) -> model.runCP(m, (cp) -> {
                DFSearch search = cp.dfSearch(branching);
                return search.solve();
            });
            LinkedList<Future<SearchStatistics>> results = new LinkedList<>();

            // Create subproblems and start EPS
            model.runCP((cp) -> {
                BestFirstSearch<Double> search = cp.bestFirstSearch(branching, () -> -CartesianSpaceEvaluator.evaluate(q));
                search.onSolution(() -> {
                    Model m = cp.symbolicCopy();
                    results.add(executorService.submit(() -> epsSolve.apply(m)));
                });
                int count = search.solve(ss -> ss.numberOfNodes() > 1000).numberOfSolutions();
                for (SymbolicModel m : search.getUnexploredModels()) {
                    results.add(executorService.submit(() -> epsSolve.apply(m)));
                    count += 1;
                }
                System.out.println("Number of EPS subproblems generated: " + count);
            });

            int count = 0;
            for (var fr : results) {
                try {
                    count += fr.get().numberOfSolutions();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Total number of solutions (in EPS): " + count);
            executorService.shutdown();
        });
        System.out.println("Time taken for EPS resolution: " + (time3/1000000000.));
    }
}