/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.magicsquare;


import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.AllDifferentDC;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.Arrays;

/**
 * The Magic Square problem.
 * <a href="http://csplib.org/Problems/prob019/">CSPLib</a>.
 */
public class MagicSquare {

    //
    public static void main(String[] args) {

        int n = 6;
        int sumResult = n * (n * n + 1) / 2;

        CPSolver cp = CPFactory.makeSolver();
        CPIntVar[][] x = new CPIntVar[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                x[i][j] = CPFactory.makeIntVar(cp, 1, n * n);
            }
        }


        CPIntVar[] xFlat = new CPIntVar[x.length * x.length];
        for (int i = 0; i < x.length; i++) {
            System.arraycopy(x[i], 0, xFlat, i * x.length, x.length);
        }


        // AllDifferent
        //cp.post(CPFactory.allDifferent(xFlat));
        cp.post(new AllDifferentDC(xFlat));

        // Sum on lines
        for (int i = 0; i < n; i++) {
            cp.post(CPFactory.sum(x[i], sumResult));
        }

        // Sum on columns
        for (int j = 0; j < x.length; j++) {
            CPIntVar[] column = new CPIntVar[n];
            for (int i = 0; i < x.length; i++)
                column[i] = x[i][j];
            cp.post(CPFactory.sum(column, sumResult));
        }

        // Sum on diagonals
        CPIntVar[] diagonalLeft = new CPIntVar[n];
        CPIntVar[] diagonalRight = new CPIntVar[n];
        for (int i = 0; i < x.length; i++) {
            diagonalLeft[i] = x[i][i];
            diagonalRight[i] = x[n - i - 1][i];
        }
        cp.post(CPFactory.sum(diagonalLeft, sumResult));
        cp.post(CPFactory.sum(diagonalRight, sumResult));

        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(xFlat));
        //DFSearch dfs = CPFactory.makeDfs(cp, Searches.staticOrder(xFlat));

        dfs.onSolution(() -> {
                    for (int i = 0; i < n; i++) {
                        System.out.println(Arrays.toString(x[i]));
                    }
                }
        );

        SearchStatistics stats = dfs.solve(stat -> stat.numberOfSolutions() >= 1); // stop on first solution

        System.out.println(stats);
    }

}
