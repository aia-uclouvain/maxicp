/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;


import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.MinimizeObjectiveSum;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.Arrays;
import java.util.Random;

import static org.maxicp.cp.CPFactory.*;

/**
 * The Magic Square problem.
 * <a href="http://csplib.org/Problems/prob019/">CSPLib</a>.
 *
 * We solve the magic-square problem using LNS.
 * The magic-square problem is a ure feasibility problem but
 * we turn it into a minimization problem by adding a violation
 * for each sum row constraint measuring the gap from the target sum.
 *
 * We follow the approach described in the paper:
 * Variable Objective Large Neighborhood Search:
 * A practical approach to solve over-constrained problems (ICTAI 2013, Pierre Schaus)
 *
 */
public class MagicSquareVOLNS {

    int n = 0;
    private int iter = 0;

    CPSolver cp = CPFactory.makeSolver();
    CPIntVar[][] x;
    CPIntVar[] xFlat;
    CPIntVar[] rowViolation; // violation for each row
    CPIntVar totalViolation; // total violation = sum of row violation


    public MagicSquareVOLNS(int n) {
        this.n = n;
        x = new CPIntVar[n][n];
        rowViolation = new CPIntVar[n];
    }

    public void modelConstraints() {
        int sumResult = n * (n * n + 1) / 2;

        cp = CPFactory.makeSolver();
        x = new CPIntVar[n][n];

        rowViolation = new CPIntVar[n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                x[i][j] = CPFactory.makeIntVar(cp, 1, n * n);
            }
        }

        xFlat = new CPIntVar[x.length * x.length];
        for (int i = 0; i < x.length; i++) {
            System.arraycopy(x[i], 0, xFlat, i * x.length, x.length);
        }

        // AllDifferent
        cp.post(CPFactory.allDifferent(xFlat));

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
        cp.post(sum(diagonalLeft, sumResult));
        cp.post(sum(diagonalRight, sumResult));


        // Turn the sum on row constraints as soft constraints
        // with violations that we want to minimize.
        for (int i = 0; i < n; i++) {
            CPIntVar rowSum = sum(x[i]);
            rowViolation[i] = abs(minus(rowSum,sumResult));
        }

        totalViolation = sum(rowViolation);
    }



    //
    public void search() {

        System.out.println(Arrays.toString(rowViolation));
        MinimizeObjectiveSum globalObjective = new MinimizeObjectiveSum(rowViolation);

        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFail(xFlat));
        
        int [] totalViolationSol = new int[] {0};
        int[] xFlatSol = new int[n*n];

        iter = 0;
        dfs.onSolution(() -> {
            System.out.println("----------------"+iter+"------------------");
            totalViolationSol[0] = totalViolation.min();
            System.out.println("totalViolation = " + totalViolationSol[0]);
            for (int j = 0 ; j < n*n ; j++) {
                xFlatSol[j] = xFlat[j].min();
            }
        });


        // start first looking for a feasible solution
        SearchStatistics stats = dfs.optimize(globalObjective, searchStatistics -> searchStatistics.numberOfSolutions() >= 1);

        int maxIter = 1000000;
        double relax = 0.10;
        Random random = new Random(42);
        
        for (iter = 0 ; iter < maxIter &&  globalObjective.getBound() > 0 ; iter++) {

            globalObjective.weakTightenAll();
            globalObjective.strongTightenSum();
            globalObjective.strongTigthenWorseTerm();

            double relaxValue = random.nextDouble();

            SearchStatistics statistics = dfs.optimizeSubjectTo(globalObjective, s -> s.numberOfFailures() >= 100, () -> {
                // relaxation
                for (int j = 0; j < n * n; ++j) {
                    if (random.nextDouble() > relaxValue) {
                        cp.post(equal(xFlat[j], xFlatSol[j]));
                    }
                }
            });
        }
        System.out.println("solution found #iter: "+iter);

    }

    public static void main(String[] args) {
        int n = 10;
        MagicSquareVOLNS magicSquare = new MagicSquareVOLNS(n);
        magicSquare.modelConstraints();
        magicSquare.search();
    }

}
