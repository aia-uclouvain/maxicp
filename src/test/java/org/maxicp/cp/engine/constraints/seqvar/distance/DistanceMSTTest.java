package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.junit.jupiter.api.Test;
import org.maxicp.cp.engine.constraints.seqvar.MinimumSpanningTree;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DistanceMSTTest extends DistanceTest{
    @Override
    protected CPConstraint getDistanceConstraint(CPSeqVar seqVar, int[][] transitions, CPIntVar distance) {
        return new DistanceMST(seqVar, transitions, distance);
    }

    @Test
    public void testDetectInfeasibility() {
        super.testDetectInfeasibility();
    }

    @Test
    public void testMST1() {
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

        //make cost symmetric
        for (int i = 0; i < nNodes; i++) {
            for (int j = i + 1; j < nNodes; j++) {
                cost[j][i] = Math.max(cost[i][j], cost[j][i]);
                cost[i][j] = cost[j][i];
            }
        }

        int[][] adjacencyMatrix = new int[nNodes][nNodes];
        for (int i = 0; i < nNodes; i++) {
            for (int j = i; j < nNodes; j++) {
                if (cost[i][j] > 0) {
                    adjacencyMatrix[i][j] = 1; // edge exists
                    adjacencyMatrix[j][i] = 1;
                }
            }
        }

        MinimumSpanningTree arb = new MinimumSpanningTree(nNodes, start, cost);
        arb.primMST(adjacencyMatrix);
        assertEquals(11, arb.getCostMinimumSpanningTree());
        assertEquals(Arrays.toString(new int[]{-1, 2, 0, 4, 1, 3}), Arrays.toString(arb.getPredsInMST()));
//        assertEquals(4, arb.arcCostMaxInPath(0, 5));
    }

    @Test
    public void testMST2() {
        int nNodes = 7;
        int start = 0;
        int[][] cost = new int[][]{
                {0, 3, 0, 0, 0, 0, 0},
                {0, 0, 2, 2, 0, 0, 0},
                {0, 0, 0, 1, 0, 0, 0},
                {0, 0, 1, 0, 0, 3, 0},
                {0, 0, 0, 0, 0, 1, 0},
                {0, 0, 0, 0, 0, 0, 2},
                {0, 0, 0, 0, 2, 0, 0}
        };

        //make cost symmetric
        for (int i = 0; i < nNodes; i++) {
            for (int j = i + 1; j < nNodes; j++) {
                cost[j][i] = Math.max(cost[i][j], cost[j][i]);
                cost[i][j] = cost[j][i];
            }
        }

        int[][] adjacencyMatrix = new int[nNodes][nNodes];
        for (int i = 0; i < nNodes; i++) {
            for (int j = i; j < nNodes; j++) {
                if (cost[i][j] > 0) {
                    adjacencyMatrix[i][j] = 1; // edge exists
                    adjacencyMatrix[j][i] = 1;
                }
            }
        }

        MinimumSpanningTree arb = new MinimumSpanningTree(nNodes, start, cost);
        arb.primMST(adjacencyMatrix);
        assertEquals(12, arb.getCostMinimumSpanningTree());
        assertEquals(Arrays.toString(new int[]{-1, 0, 1, 2, 5, 3, 5}), Arrays.toString(arb.getPredsInMST()));
//        assertEquals(3, arb.arcCostMaxInPath(0, 5));
    }
}
