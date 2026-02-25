package org.maxicp.cp.examples.modeling.tsp;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.examples.utils.TSPInstance;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.bool.Eq;
import org.maxicp.modeling.algebra.bool.NotEq;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.io.InputReader;

import java.util.function.Function;
import java.util.stream.IntStream;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

public class TSPBoundImpact {

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
        baseModel.runCP((cp) -> {
            Function<IntExpression, Integer> valueSelector = boundImpactValueSelector(totalDist).get();
            DFSearch search = cp.dfSearch(() -> {
                IntVar xs = selectMin(successor, xi -> !xi.isFixed(), xi -> xi.size());
                if (xs == null)
                    return EMPTY;
                else {
                    Integer v = valueSelector.apply(xs);
                    //if (v == null)
                    //    throw InconsistencyException.INCONSISTENCY;
                    int value = v == null ? xs.min() : v;
                    return branch(() -> baseModel.add(new Eq(xs, value)),
                            () -> baseModel.add(new NotEq(xs, value)));
                }
            });
            // print each solution found
            search.onSolution(() -> {
                System.out.println(totalDist);
            });
            SearchStatistics statistics = search.optimize(minimizeDistance); // actually solve the problem
            System.out.println(statistics);
        });

    }

}
