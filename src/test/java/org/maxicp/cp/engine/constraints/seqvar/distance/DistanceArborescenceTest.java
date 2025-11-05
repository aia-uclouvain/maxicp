package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.junit.jupiter.api.Test;
import org.maxicp.cp.engine.constraints.seqvar.MinimumArborescence;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.cp.CPFactory.makeSolver;

public class DistanceArborescenceTest extends DistanceTest{
    @Override
    protected CPConstraint getDistanceConstraint(CPSeqVar seqVar, int[][] transitions, CPIntVar distance) {
        return new DistanceArborescence(seqVar, transitions, distance);
    }

    @Test
    public void testMinArborescence1() {
        int nNodes = 6;
        int start = 0;
        int[][] cost = new int[][]{
                {0, 10, 2, 10, 0, 0},
                {0, 0, 1, 0, 0, 8},
                {0, 0, 0, 4, 0, 0},
                {0, 0, 0, 0, 2, 4},
                {0, 2, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0}
        };

        int[][] preds = new int[nNodes][nNodes];
        int[] numPreds = new int[nNodes];
        for (int i = 0; i < nNodes; i++) {
            for (int j = 0; j < nNodes; j++) {
                if (cost[i][j] > 0) {
                    preds[j][numPreds[j]] = i;
                    numPreds[j]++;
                }
            }
        }

        MinimumArborescence arb = new MinimumArborescence(cost, start);
        arb.findMinimumArborescence(preds, numPreds);
        assertEquals(14, arb.getCostMinimumArborescence());
    }

    @Test
    public void testMinArborescence2() {
        int nNodes = 7;
        int start = 0;
        int[][] cost = new int[][]{
                {0, 3, 0, 0, 0, 0, 0},
                {0, 0, 0, 2, 0, 0, 0},
                {0, 0, 0, 1, 0, 0, 0},
                {0, 0, 3, 0, 0, 3, 0},
                {0, 0, 0, 0, 0, 1, 0},
                {0, 0, 0, 0, 0, 0, 2},
                {0, 0, 0, 0, 2, 0, 0}
        };

        int[][] preds = new int[nNodes][nNodes];
        int[] numPreds = new int[nNodes];
        for (int i = 0; i < nNodes; i++) {
            for (int j = 0; j < nNodes; j++) {
                if (cost[i][j] > 0) {
                    preds[j][numPreds[j]] = i;
                    numPreds[j]++;
                }
            }
        }

        MinimumArborescence arb = new MinimumArborescence(cost, start);
        arb.findMinimumArborescence(preds, numPreds);
        assertEquals(15, arb.getCostMinimumArborescence());
    }
}
