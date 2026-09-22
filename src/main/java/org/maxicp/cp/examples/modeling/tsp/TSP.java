/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling.tsp;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.examples.utils.TSPInstance;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;
import org.maxicp.util.io.InputReader;

import java.util.stream.IntStream;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.firstFailBinary;

public class TSP {

    public static void main(String[] args) {

        TSPInstance instance = new TSPInstance("data/TSP/gr21.xml");
        int n = instance.n;
        int[][] distanceMatrix = instance.distanceMatrix;

        // create a model of the problem
        ModelDispatcher baseModel = Factory.makeModelDispatcher();
        // successor of each city in a TSP tour
        IntVar[] successor = baseModel.intVarArray(n, n);
        // distance between a city and its successor
        IntExpression[] distToSuccessor = IntStream.range(0, n)
                .mapToObj(city -> get(distanceMatrix[city], successor[city]))
                .toArray(IntExpression[]::new);
        // the successors must define a circuit between all cities
        baseModel.add(circuit(successor));
        // objective is the sum of distances
        IntExpression totalDist = sum(distToSuccessor);
        // objective needs to be minimized
        Objective minimizeDistance = minimize(totalDist);

        // run with a search procedure
        long init = System.currentTimeMillis();
        baseModel.runCP((cp) -> {
            DFSearch search = cp.dfSearch(Searches.firstFailBinary(successor));
            // print each solution found
            search.onSolution(() -> {
                System.out.println(totalDist);
            });
            search.optimize(minimizeDistance); // actually solve the problem
        });
        double elapsed = (System.currentTimeMillis() - init) / 1000.0;
        System.out.printf("solved in %.3f s%n", elapsed);

        // run with best first search
        /*
        int[] remainingSuccessors = new int[n];
        init = System.currentTimeMillis();
        baseModel.runAsConcrete(CPModelInstantiator.withTrailing, (cp) -> {
            BestFirstSearch<Double> search = cp.bestFirstSearch(firstFail(successor), () -> {
                return CartesianSpaceEvaluator.evaluate(successor);
            });
            // print each solution found
            search.onSolution(() -> {
                System.out.println(totalDist.show());
            });
            search.solve(); // actually solve the problem
        });
        elapsed = (System.currentTimeMillis() - init) / 1000.0;
        System.out.printf("solved in %.3f s%n", elapsed);
        */

    }

}
