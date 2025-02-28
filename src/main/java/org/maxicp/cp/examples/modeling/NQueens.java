/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;


import org.maxicp.ModelDispatcher;
import org.maxicp.cp.modeling.CPModelInstantiator;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.Model;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.modeling.algebra.bool.NotEq;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.AllDifferent;
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
        ModelDispatcher baseModel = Factory.makeModelDispatcher();

        IntVar[] q = baseModel.intVarArray(n, n);
        IntExpression[] qLeftDiagonal = IntStream.range(0, q.length).mapToObj(i -> q[i].plus(i)).toArray(IntExpression[]::new);
        IntExpression[] qRightDiagonal =  IntStream.range(0, q.length).mapToObj(i -> q[i].minus(i)).toArray(IntExpression[]::new);

        baseModel.add(new AllDifferent(q));
        baseModel.add(new AllDifferent(qLeftDiagonal));
        baseModel.add(new AllDifferent(qRightDiagonal));

        //baseModel.add(new AllDifferentPersoCP.mconstraint(q[0], q[1]));

        Supplier<Runnable[]> branching = () -> {
            int idx = -1; // index of the first variable that is not fixed
            for (int k = 0; k < q.length; k++)
                if (!q[k].isFixed()) {
                    idx=k;
                    break;
                }
            if (idx == -1)
                return EMPTY;
            else {
                IntExpression qi = q[idx];
                int v = qi.min();
                Runnable left = () -> baseModel.add(new Eq(qi, v));
                Runnable right = () -> baseModel.add(new NotEq(qi, v));
                return branch(left,right);
            }
        };

        //
        // Basic standard solving demo
        //
        System.out.println("--- SIMPLE SOLVING");
        long time = TimeIt.run(() -> {

            baseModel.runCP((cp) -> {
                DFSearch search = cp.dfSearch(branching);
                System.out.println("Total number of solutions: " + search.solve().numberOfSolutions());
            });
        });
        System.out.println("Time taken for simple resolution: " + (time/1000000000.));


        //
        // Basic EPS solving demo
        //
        System.out.println("--- EPS (DFS for decomposition)");
        long time2 = TimeIt.run(() -> {
            ExecutorService executorService = Executors.newFixedThreadPool(8);

            Function<Model, SearchStatistics> epsSolve = (m) -> {
                return baseModel.runAsConcrete(CPModelInstantiator.withTrailing, m, (cp) -> {
                    DFSearch search = cp.dfSearch(branching);
                    return search.solve();
                });
            };
            LinkedList<Future<SearchStatistics>> results = new LinkedList<>();

            // Create subproblems and start EPS
            baseModel.runCP((cp) -> {
                DFSearch search = cp.dfSearch(new LimitedDepthBranching(branching, 10));
                search.onSolution(() -> {
                    Model m = cp.symbolicCopy();
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

            Function<Model, SearchStatistics> epsSolve = (m) -> baseModel.runCP(m, (cp) -> {
                DFSearch search = cp.dfSearch(branching);
                return search.solve();
            });
            LinkedList<Future<SearchStatistics>> results = new LinkedList<>();

            // Create subproblems and start EPS
            baseModel.runCP((cp) -> {
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