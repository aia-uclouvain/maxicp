/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.examples.modeling;


import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.modeling.Factory.*;


public class ComplexExpressionsTest extends CPSolverTest {

    public static <T> Set<List<T>> cartesianProduct(Set<T>[] sets) {
        Set<List<T>> result = new HashSet<>();
        if (sets.length == 0) {
            return result;
        }
        cartesianProductHelper(sets, 0, new ArrayList<>(), result);
        return result;
    }

    private static <T> void cartesianProductHelper(Set<T>[] sets, int index, List<T> current, Set<List<T>> result) {
        if (index == sets.length) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (T element : sets[index]) {
            current.add(element);
            cartesianProductHelper(sets, index + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    @Test
    public void test() {




        Set<Integer> [] domains = new Set[]{Set.of(-3,2,5,0),Set.of(-1,-2,3,4),Set.of(1,2,-3,4),Set.of(1,2,3,-4),Set.of(1,-2,3,4)};

        Set<List<Integer>> res = cartesianProduct(domains);
        int nbSol = 0;
        for (List<Integer> l : res) {
            // ((((3 * x[0]) + (2 * x[1])) * x[2]) - x[3]) + x[4]
            int result = ((((3*l.get(0)) + (2*l.get(1))) * l.get(2)) - l.get(3)) + l.get(4);
            if (result == 23) {
                nbSol++;
                System.out.println("Solution found: " + l);
            }
            //System.out.println();
        }

        ModelDispatcher model = Factory.makeModelDispatcher();

        IntExpression [] x = model.intVarArray(5, i -> model.intVar(domains[i]));

        IntExpression expr = sum(mul(sum(mul(3,x[0]),mul(2,x[1])),x[2]),Factory.minus(x[3]),x[4]);

        model.add(eq(expr,23));

        ConcreteCPModel cp = model.cpInstantiate();

        DFSearch dfs = cp.dfSearch(Searches.staticOrder(x));

        SearchStatistics stats = dfs.solve(); // actually solve the problem

        System.out.println("Number of solutions: " + stats.numberOfSolutions());
        System.out.println("Number of solutions found by brute force: " + nbSol);


    }


}
