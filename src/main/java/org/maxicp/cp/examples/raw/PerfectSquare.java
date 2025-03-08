package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.Sum;
import org.maxicp.cp.engine.constraints.scheduling.CPCumulFunction;
import org.maxicp.cp.engine.constraints.scheduling.IsEndAfter;
import org.maxicp.cp.engine.constraints.scheduling.IsStartBefore;
import org.maxicp.cp.engine.core.*;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.scheduling.IntervalExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.function.Supplier;

import static org.maxicp.search.Searches.*;

public class PerfectSquare {

    public static void main(String[] args) {
        int side = 5;
        int[] sides = new int[] {3, 2, 2, 2, 1, 1, 1, 1};
        int nSquares = sides.length;
        CPSolver cp = CPFactory.makeSolver();
        CPIntervalVar[] squareX = new CPIntervalVar[nSquares]; // x coordinates for the start of each smaller square
        CPIntervalVar[] squareY = new CPIntervalVar[nSquares]; // y coordinates for the start of each smaller square
        CPCumulFunction xConsumption = CPFactory.flat(); // resource consumption over x axis
        CPCumulFunction yConsumption = CPFactory.flat(); // resource consumption over y axis
        for (int square = 0 ; square < nSquares ; square++) {
            int length = sides[square];
            // a small square is contained within the big one, and has a predefined length
            squareX[square] = CPFactory.makeIntervalVar(cp, false, length, length);
            squareX[square].setStartMin(0);
            squareX[square].setStartMax(side - length);
            squareX[square].setEndMin(length);
            squareX[square].setEndMax(side);

            squareY[square] = CPFactory.makeIntervalVar(cp, false, length, length);
            squareY[square].setStartMin(0);
            squareY[square].setStartMax(side - length);
            squareY[square].setEndMin(length);
            squareY[square].setEndMax(side);

            // each square occupies a given area
            xConsumption = CPFactory.sum(xConsumption, CPFactory.pulse(squareX[square], length));
            yConsumption = CPFactory.sum(yConsumption, CPFactory.pulse(squareY[square], length));
        }
        cp.post(CPFactory.alwaysIn(xConsumption, side, side));
        cp.post(CPFactory.alwaysIn(yConsumption, side, side));

        // no overlap between the squares
        for (int i = 0 ; i <nSquares ; i++) {
            for (int j = i + 1 ; j <nSquares ; j++) {
                // start(i) <= end(j) || start(j) <= end(i), over the 2 dimensions
                cp.post(CPFactory.or(CPFactory.isEndBeforeStart(squareX[i], squareX[j]), CPFactory.isEndBeforeStart(squareX[j], squareX[i]),
                        CPFactory.isEndBeforeStart(squareY[i], squareY[j]), CPFactory.isEndBeforeStart(squareY[j], squareY[i])));
            }
        }

        // redundant constraint: for each x-tick, the sum of height of the squares == side
        for (CPIntervalVar[] squares: new CPIntervalVar[][] {squareX, squareY}) {
            for (int tick = 0; tick < side; tick++) {
                CPIntVar[] heightOnTick = new CPIntVar[nSquares];
                CPIntVar tickExpr = new CPIntVarConstant(cp, tick);
                for (int i = 0; i < nSquares; i++) {
                    int length = sides[i];
                    CPBoolVar isStartBeforeTick = CPFactory.makeBoolVar(cp);
                    cp.post(new IsStartBefore(isStartBeforeTick, squares[i], tickExpr)); // before or at the tick
                    CPBoolVar isEndAfterTick = CPFactory.makeBoolVar(cp);
                    cp.post(new IsEndAfter(isEndAfterTick, squares[i], CPFactory.plus(tickExpr, 1))); // end after or at tick + 1
                    // logical and
                    CPIntVar conditions = CPFactory.makeIntVar(cp, 0, 2);
                    cp.post(new Sum(new CPIntVar[]{isEndAfterTick, isStartBeforeTick}, conditions));
                    CPBoolVar squarePlacedOnTick = CPFactory.isEq(conditions, 2);
                    heightOnTick[i] = CPFactory.mul(squarePlacedOnTick, length);
                }
                cp.post(CPFactory.sum(heightOnTick, side));
            }
        }

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
        DFSearch search = CPFactory.makeDfs(cp, branching);
        SearchStatistics stats = search.solve();
        System.out.println("stats: \n" + stats);

    }

}
