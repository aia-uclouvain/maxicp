/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;


import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.AllDifferent;
import org.maxicp.search.SearchMethod;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.TimeIt;

import java.util.Arrays;

import static org.maxicp.modeling.Factory.eq;
import static org.maxicp.modeling.Factory.sum;

/**
 * The Magic Square problem.
 * <a href="http://csplib.org/Problems/prob019/">CSPLib</a>.
 */
public class MagicSquare {

    //
    public static void main(String[] args) {


        int n = 5;
        int sumResult = n * (n * n + 1) / 2;

        ModelDispatcher model = Factory.makeModelDispatcher();

        IntVar[][] x = new IntVar[n][n];

        model.intVarArray(n, n);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                x[i][j] = model.intVar(1,n*n);
            }
        }

        IntVar[] xFlat = new IntVar[x.length * x.length];
        for (int i = 0; i < x.length; i++) {
            System.arraycopy(x[i], 0, xFlat, i * x.length, x.length);
        }

        // AllDifferent
        model.add(new AllDifferent(xFlat));

        // Sum on lines
        for (int i = 0; i < n; i++) {
            model.add(eq(sum(x[i]),sumResult));
        }

        // Sum on columns
        for (int j = 0; j < x.length; j++) {
            IntVar[] column = new IntVar[n];
            for (int i = 0; i < x.length; i++) {
                column[i] = x[i][j];
            }
            model.add(eq(sum(column),sumResult));
        }

        // Sum on diagonals
        IntVar[] diagonalLeft = new IntVar[n];
        IntVar[] diagonalRight = new IntVar[n];
        for (int i = 0; i < x.length; i++) {
            diagonalLeft[i] = x[i][i];
            diagonalRight[i] = x[n - i - 1][i];
        }
        model.add(eq(sum(diagonalLeft),sumResult));
        model.add(eq(sum(diagonalRight),sumResult));

        long time = TimeIt.run(() -> {
            model.runCP(() -> {
                SearchMethod search = model.dfSearch(Searches.conflictOrderingSearch(Searches.minDomVariableSelector(xFlat), var -> var.min()));
                //DFSearch search = cp.dfSearch(Searches.staticOrder(xFlat));
                search.onSolution(() -> {
                    for (int i = 0; i < n; i++) {
                        System.out.println(Arrays.toString(x[i]));
                    }
                });
                SearchStatistics stats = search.solve(stat -> stat.numberOfSolutions() >= 1);
                System.out.println(stats);
            });
        });
        System.out.println(time);
    }

}
