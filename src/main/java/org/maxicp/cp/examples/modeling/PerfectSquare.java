package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.bool.BoolExpression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.algebra.scheduling.IntervalExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.function.Supplier;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

/**
 * Perfect Square Problem
 *
 * The problem is to fully cover a big square with different smaller ones, with no overlap between them
 */
public class PerfectSquare {

    public static void main(String[] args) {
        // side of the big square
        int side = 112;
        // side of the smaller squares that must be put in the big one
        int[] sides = new int[] {50, 42, 37, 35, 33, 29, 27, 25, 24, 19, 18, 17, 16, 15, 11, 9, 8, 7, 6, 4, 2};

        int nSquares = sides.length;
        ModelDispatcher baseModel = Factory.makeModelDispatcher();
        IntervalVar[] squareX = new IntervalVar[nSquares]; // x coordinates for the start of each smaller square
        IntervalVar[] squareY = new IntervalVar[nSquares]; // y coordinates for the start of each smaller square
        CumulFunction xConsumption = flat(); // resource consumption over x axis
        CumulFunction yConsumption = flat(); // resource consumption over y axis
        for (int square = 0 ; square < nSquares ; square++) {
            int length = sides[square];
            // a small square is contained within the big one, and has a predefined length
            squareX[square] = baseModel.intervalVar(0, side - length, length, side, length, length, true);
            squareY[square] = baseModel.intervalVar(0, side - length, length, side, length, length, true);
            // each square occupies a given area
            xConsumption = sum(xConsumption, pulse(squareX[square], length));
            yConsumption = sum(yConsumption, pulse(squareY[square], length));
        }
        // cumulative: at each x coordinate, the height of all squares must match exactly the height of the big one
        baseModel.add(alwaysIn(xConsumption, side, side));
        // cumulative over the y coordinates
        baseModel.add(alwaysIn(yConsumption, side, side));

        // no overlap between the squares
        for (int i = 0 ; i <nSquares ; i++) {
            for (int j = i + 1 ; j <nSquares ; j++) {
                // start(i) <= end(j) || start(j) <= end(i), over the 2 dimensions
                baseModel.add(or(endBeforeStart(squareX[i], squareX[j]), endBeforeStart(squareX[j], squareX[i]),
                        endBeforeStart(squareY[i], squareY[j]), endBeforeStart(squareY[j], squareY[i])));
            }
        }

        // redundant constraint: for each x-tick and y-tick, the sum of height of the squares == side
        for (IntervalVar[] squares: new IntervalVar[][] {squareX, squareY}) {
            for (int tick = 0; tick < side; tick++) {
                IntExpression[] heightOnTick = new IntExpression[nSquares];
                for (int i = 0; i < nSquares; i++) {
                    int length = sides[i];
                    BoolExpression isStartBeforeTick2 = startBefore(squares[i], tick); // before or at the tick
                    BoolExpression isEndAfterTick2 = endAfter(squares[i], tick + 1); // end after or at tick + 1
                    BoolExpression squarePlacedOnTick = and(isStartBeforeTick2, isEndAfterTick2);
                    heightOnTick[i] = mul(length, squarePlacedOnTick);
                }
                baseModel.add(eq(sum(heightOnTick), side));
            }
        }
        // run with CP
        baseModel.runCP((cp) -> {
            Supplier<Runnable[]> fixStartX = () -> {
                IntervalVar square  = selectMin(squareX, s -> !s.isFixed(), IntervalExpression::startMin);
                if (square == null)
                    return EMPTY;
                return branchOnStartMin(square);
            };
            Supplier<Runnable[]> fixStartY = () -> {
                IntervalVar square  = selectMin(squareY, s -> !s.isFixed(), IntervalExpression::startMin);
                if (square == null)
                    return EMPTY;
                return branchOnStartMin(square);
            };
            Supplier<Runnable[]> branching = and(fixStartX, fixStartY);
            DFSearch search = cp.dfSearch(branching);
            search.onSolution(() -> {
                draw(side, squareX, squareY);
            });
            // stop af first solution
            SearchStatistics stats = search.solve(s -> s.numberOfSolutions() >= 1);
            System.out.println("stats: \n" + stats);
        });


    }

    private static void draw(int side, IntervalVar[] squareX, IntervalVar[] squareY) {
        char[][] content = new char[side][side];
        char current = 'A';
        for (int square = 0 ; square < squareX.length ; square++) {
            int startX = squareX[square].startMin();
            int endX = squareX[square].endMin();
            int startY = squareY[square].startMin();
            int endY = squareY[square].endMin();
            for (int x = startX ; x < endX ; x++) {
                for (int y = startY ; y < endY ; y++) {
                    content[y][x] = current;
                }
            }
            current += 1;
        }
        for (char[] chars : content) {
            for (char aChar : chars)
                System.out.print(aChar);
            System.out.println();
        }
        System.out.println();
    }

}
