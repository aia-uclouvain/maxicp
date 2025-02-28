package org.maxicp.util;

import java.util.List;
import java.util.Random;

/**
 * Handle common operations over distance matrices
 */
public class DistanceMatrix {

    /**
     * Position between points
     * @param x x-coordinate
     * @param y y-coordinate
     */
    private record Position(double x, double y) {

        private double euclideanDistance(Position o) {
            return Math.sqrt(Math.pow(o.x - x, 2) + Math.pow(o.y - y, 2));
        }

        private int ceilEuclideanDistance(Position o) {
            return (int) Math.ceil(euclideanDistance(o));
        }

    }

    /**
     * Checks whether a distance matrix respects the triangular inequality.
     * Prints a warning message on stderr if the triangular inequality is not enforced
     * @param dist distance matrix
     */
    public static void checkTriangularInequality(int[][] dist) {
        for (int i = 0 ; i < dist.length ; i++) {
            for (int j = 0 ; j < dist[i].length ; j++) {
                int smallestDist = dist[i][j];
                for (int k = 0 ; k < dist.length ; k++) {
                    int distWithDetour = dist[i][k] + dist[k][j];
                    if (distWithDetour < smallestDist) {
                        System.err.println("[WARNING]: triangular inequality not respected with distance matrix");
                        System.err.printf("[WARNING]: dist[%d][%d] + dist[%d][%d] < dist[%d][%d] (%d + %d < %d)%n", i, k, k, j, i, j,
                                dist[i][k], dist[k][j], dist[i][j]);
                        System.err.println("[WARNING]: this might remove some solutions");
                        return;
                    }
                }
            }
        }
    }

    /**
     * Extend a matrix, adding new rows and columns corresponding to other entries already present in the matrix.
     * This is typically done for extending a distance matrix if node are duplicated
     * <p>
     * Example:
     *  - matrix = {{0, 1}, {2, 3}}
     *  - duplication = [0]
     *  - result = row 2 and column 2 added, corresponding to node 0: {{0, 1, 0}, {2, 3, 2}, {0, 1, 0}}
     *
     * @param matrix matrix that must be extended
     * @param duplication one element per new row and column to add, telling
     * @return
     */
    public static int[][] extendMatrixAtEnd(int[][] matrix, List<Integer> duplication) {
        int n = matrix.length;
        int nDuplicate = duplication.size();
        int extendedSize = matrix.length + nDuplicate;
        int[][] extendedMatrix = new int[extendedSize][extendedSize];
        /* extended matrix:
         *  A | B
         *  -----
         *  C | D
         * where A is the original matrix
         */
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, extendedMatrix[i], 0, n);  // fill A
        }
        for (int i = 0; i < nDuplicate; i++) {
            int extendedIndex = n + i;
            int originIndex = duplication.get(i);
            if (originIndex < 0 || originIndex >= n)
                throw new IllegalArgumentException(
                        String.format("Cannot duplicate row %d in matrix of size %d%n", originIndex, n));
            // fill C
            for (int j = 0 ; j < n ; j++)
                extendedMatrix[extendedIndex][j] = matrix[originIndex][j];
            // fill B
            for (int j = 0 ; j < n ; j++)
                extendedMatrix[j][extendedIndex] = matrix[j][originIndex];
        }
        // fill D
        for (int i = 0 ; i < nDuplicate ; i++) {
            int extendedIndexI = n + i;
            int originIndexI = duplication.get(i);
            for (int j = 0 ; j < nDuplicate ; j++) {
                int extendedIndexJ = n + j;
                int originIndexJ = duplication.get(j);
                extendedMatrix[extendedIndexI][extendedIndexJ] = matrix[originIndexI][originIndexJ];
            }
        }
        return extendedMatrix;
    }

    /**
     * Generate a random distance matrix where the triangular inequality is enforced
     * @param nNodes number of rows and columns in the distance matrix
     * @return distance matrix of nNodes rows and columns
     */
    public static int[][] randomDistanceMatrix(int nNodes) {
        return randomDistanceMatrix(nNodes, 1000, 42);
    }

    /**
     * Generate a random distance matrix where the triangular inequality is enforced
     * @param nNodes number of rows and columns in the distance matrix
     * @param bound for the generation, points will be considered as lying on a bound X bound square.
     *              The larger the value of bound, the larger will the distances be
     * @return distance matrix of nNodes rows and columns
     */
    public static int[][] randomDistanceMatrix(int nNodes, int bound) {
        return randomDistanceMatrix(nNodes, bound, 42);
    }

    /**
     * Generate a random distance matrix where the triangular inequality is enforced
     *
     * @param nNodes number of rows and columns in the distance matrix
     * @param bound  for the generation, points will be considered as lying on a bound X bound square.
     *               The larger the value of bound, the larger will the distances be
     * @param seed   seed used for random number generation
     * @return distance matrix of nNodes rows and columns
     */
    public static int[][] randomDistanceMatrix(int nNodes, int bound, int seed) {
        Random random = new Random(seed);
        Position[] nodes = new Position[nNodes];
        for (int i = 0 ; i < nNodes ; i++)
            nodes[i] = new Position(random.nextDouble(bound), random.nextDouble(bound));
        int[][] distance = new int[nNodes][nNodes];
        for (int i = 0 ; i < nNodes ; i++) {
            for (int j = i+1 ; j < nNodes; j++) {
                int dist = nodes[i].ceilEuclideanDistance(nodes[j]);
                distance[i][j] = dist;
            }
        }
        return distance;
    }
}
