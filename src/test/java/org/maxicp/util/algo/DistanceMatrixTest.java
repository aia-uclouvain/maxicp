package org.maxicp.util.algo;

import org.junit.jupiter.api.Test;
import org.maxicp.util.algo.DistanceMatrix;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DistanceMatrixTest {

    @Test
    void testEnforceTriangularInequalityBasic() {
        // A direct path 0->2 is 10, but 0->1->2 is 3+3=6
        int[][] dist = {
                {0, 3, 10},
                {3, 0, 3},
                {10, 3, 0}
        };

        DistanceMatrix.enforceTriangularInequality(dist);

        // dist[0][2] should be updated to 6
        assertEquals(6, dist[0][2]);
        assertEquals(6, dist[2][0]);
        assertEquals(0, dist[0][0]);
    }

    @Test
    void testExtendMatrixAtEnd() {
        int[][] matrix = {
                {0, 5},
                {2, 3}
        };
        // Duplicate index 0
        List<Integer> duplication = Arrays.asList(0);

        int[][] extended = DistanceMatrix.extendMatrixAtEnd(matrix, duplication);

        /* Expected result:
           0, 5, 0
           2, 3, 2
           0, 5, 0
        */
        assertEquals(3, extended.length);
        assertEquals(0, extended[2][0]);
        assertEquals(5, extended[2][1]);
        assertEquals(2, extended[1][2]);
        assertEquals(0, extended[2][2]);
    }

    @Test
    void testRandomDistanceMatrixConsistency() {
        int n = 5;
        int[][] dist1 = DistanceMatrix.randomDistanceMatrix(n, 100, 42);
        int[][] dist2 = DistanceMatrix.randomDistanceMatrix(n, 100, 42);

        // Check if seed works
        assertArrayEquals(dist1, dist2);
        assertEquals(n, dist1.length);
        assertEquals(n, dist1[0].length);
    }
}