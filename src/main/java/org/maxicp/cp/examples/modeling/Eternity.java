/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;

import static org.maxicp.modeling.Factory.*;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.AllDifferent;
import org.maxicp.modeling.constraints.Table;
import org.maxicp.search.SearchMethod;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.TimeIt;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;

import static org.maxicp.modeling.Factory.eq;
import static org.maxicp.search.Searches.and;
import static org.maxicp.search.Searches.firstFail;

/**
 * The Eternity II puzzle is an edge-matching puzzle which
 * involves placing 256 square puzzle pieces into a 16 by 16 grid,
 * constrained by the requirement to match adjacent edges.
 * <a href="https://en.wikipedia.org/wiki/Eternity_II_puzzle">Wikipedia.</a>
 */
public class Eternity {

    public static IntVar[] flatten(IntExpression[][] x) {
        return Arrays.stream(x).flatMap(Arrays::stream).toArray(IntVar[]::new);
    }

    public static void main(String[] args) {

        // Reading the data

        InputReader reader = new InputReader("data/ETERNITY/eternity7x7.txt");

        int n = reader.getInt();
        int m = reader.getInt();

        int[][] pieces = new int[n * m][4];
        int maxTmp = 0;

        for (int i = 0; i < n * m; i++) {
            for (int j = 0; j < 4; j++) {
                pieces[i][j] = reader.getInt();
            }
        }

        // Create the table:
        // Each line correspond to one possible rotation of a piece
        // For instance if the line piece[6] = [2,3,5,1]
        // the four lines created in the table are
        // [6,2,3,5,1] // rotation of 0°
        // [6,3,5,1,2] // rotation of 90°
        // [6,5,1,2,3] // rotation of 180°
        // [6,1,2,3,5] // rotation of 270°

        int[][] table = new int[4 * n * m][5];

        for (int i = 0; i < pieces.length; i++) {
            for (int r = 0; r < 4; r++) {
                table[i * 4 + r][0] = i;
                table[i * 4 + r][1] = pieces[i][(r + 0) % 4];
                table[i * 4 + r][2] = pieces[i][(r + 1) % 4];
                table[i * 4 + r][3] = pieces[i][(r + 2) % 4];
                table[i * 4 + r][4] = pieces[i][(r + 3) % 4];
            }
        }

        final int max = Arrays.stream(pieces).flatMapToInt(Arrays::stream).max().getAsInt();


        ModelDispatcher baseModel = makeModelDispatcher();

        //   |         |
        // - +---------+- -
        //   |    u    |
        //   | l  i  r |
        //   |    d    |
        // - +---------+- -
        //   |         |


        IntExpression[][] id = new IntExpression[n][m]; // id
        IntExpression[][] u = new IntExpression[n][m];  // up
        IntExpression[][] r = new IntExpression[n][m];  // right
        IntExpression[][] d = new IntExpression[n][m];  // down
        IntExpression[][] l = new IntExpression[n][m];  // left

        for (int i = 0; i < n; i++) {
            u[i] = baseModel.intVarArray(m, j -> baseModel.intVar(0, max));
            id[i] = baseModel.intVarArray(m, n * m);
        }
        for (int k = 0; k < n; k++) {
            final int i = k;
            if (i < n - 1) d[i] = u[i + 1];
            else d[i] = baseModel.intVarArray(m, j -> baseModel.intVar(0, max));
        }
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < n; i++) {
                l[i][j] = baseModel.intVar(0, max);
            }
        }
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < n; i++) {
                if (j < m - 1) r[i][j] = l[i][j + 1];
                else r[i][j] = baseModel.intVar(0, max);
            }
        }

        // Constraint1: all the pieces placed are different
        baseModel.add(allDifferent(flatten(id)));

        // Constraint2: all the pieces placed are valid ones i.e. one of the given mxn pieces possibly rotated
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                baseModel.add(new Table(new IntExpression[]{id[i][j], u[i][j], r[i][j], d[i][j], l[i][j]}, table));
            }
        }

        // Constraint3: place "0" the borders (gray color)
        for (int i = 0; i < n; i++) {
            baseModel.add(eq(l[i][0], 0));
            baseModel.add(eq(r[i][m - 1], 0));
        }
        for (int j = 0; j < m; j++) {
            baseModel.add(eq(u[0][j], 0));
            baseModel.add(eq(d[n - 1][j], 0));
        }

        long time = TimeIt.milliSeconds(() -> {
            baseModel.runCP(() -> {
                SearchMethod search = baseModel.dfSearch(and(firstFail(flatten(id)),
                        firstFail(flatten(u)),
                        firstFail(flatten(r)),
                        firstFail(flatten(d)),
                        firstFail(flatten(l))));

                search.onSolution(() -> {
                    System.out.println("----------------");
                    prettyPrint(u, l, r, d);
                });
                SearchStatistics stats = search.solve(stat -> stat.numberOfSolutions() >= 1);
                System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
                System.out.format("Statistics: %s\n", stats);

            });
        });
        System.out.format("time: %s (ms)\n", time);

    }


    public static void prettyPrint(IntExpression [][] u, IntExpression [][] l, IntExpression [][] r, IntExpression [][] d) {
        for (int i = 0; i < u.length; i++) {
            String line = "   ";
            for (int j = 0; j < u[i].length; j++) {
                line += u[i][j].min() + "   ";
            }
            System.out.println(line);
            line = " ";
            for (int j = 0; j < l[i].length; j++) {
                line += l[i][j].min() + "   ";
            }
            line += r[i][l[i].length - 1].min();
            System.out.println(line);
        }
        String line = "   ";
        for (int j = 0; j < d[d.length-1].length; j++) {
            line += d[d.length-1][j].min() + "   ";
        }
        System.out.println(line);
    }
}
