package org.maxicp.cp.examples.utils;

import org.maxicp.util.algo.DistanceMatrix;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;

/**
 * A TSP with Time Windows instance.
 * The depot is duplicated at the end of the instance.
 * So we don't seek for a tour but for a path from the depot to its duplicate at the end
 */
public class TSPTWInstance {

    public int n;
    public int[][] distMatrix;
    public int[] earliest, latest;
    public int horizon = Integer.MIN_VALUE;

    public TSPTWInstance(String file) {
        InputReader reader = new InputReader(file);
        n = reader.getInt();
        distMatrix = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                distMatrix[i][j] = reader.getInt();
            }
        }
        earliest = new int[n];
        latest = new int[n];

        for (int i = 0; i < n; i++) {
            earliest[i] = reader.getInt();
            latest[i] = reader.getInt();
            horizon = Math.max(horizon, latest[i] + 1);
        }

        // duplicate the depot at the end
        int[][] newDistMatrix = new int[n+1][n+1];
        int[] newEarliest = new int[n+1];
        int[] newLatest = new int[n+1];
        for (int i = 0; i < n; i++) {
            newEarliest[i] = earliest[i];
            newLatest[i] = latest[i];
            for (int j = 0; j < n; j++) {
                newDistMatrix[i][j] = distMatrix[i][j];
            }
        }
        // depot
        newEarliest[n] = earliest[0];
        newLatest[n] = latest[0];
        for (int j = 0; j < n; j++) {
            newDistMatrix[n][j] = distMatrix[0][j];
            newDistMatrix[j][n] = distMatrix[j][0];
        }
        distMatrix = newDistMatrix;
        earliest = newEarliest;
        latest = newLatest;
        DistanceMatrix.enforceTriangularInequality(newDistMatrix);
        n = n + 1;
    }

    private TSPTWInstance(int[][] distMatrix, int[] E, int[] L) {
        n = E.length;
        this.earliest = E;
        this.latest = L;
        this.distMatrix = distMatrix;
        for (int i = 0; i < n; i++) {
            horizon = Math.max(horizon, L[i] + 1);
        }
    }

    @Override
    public String toString() {
        return "Instance{" +
                "n=" + n + "\n" +
                ", distMatrix=" + Arrays.deepToString(distMatrix) + "\n" +
                ", E=" + Arrays.toString(earliest) + "\n" +
                ", L=" + Arrays.toString(latest) + "\n" +
                ", horizon=" + horizon +
                '}';
    }

    public boolean checkSolution(int[] tour, int totalDistance) {
        // check time windows
        int time = 0;
        int computedDistance = 0;
        for (int i = 0; i < tour.length - 1; i++) {
            int from = tour[i];
            int to = tour[i + 1];
            time += distMatrix[from][to];
            computedDistance += distMatrix[from][to];
            // can wait if arrive early
            if (time < earliest[to]) {
                time = earliest[to];
            }
            if (time < earliest[to] || time > latest[to]) {
                System.out.println("time window violated at node " + to + ": arrival time " + time +", window [" + earliest[to] + "," + latest[to] + "]");
                return false;
            }
        }
        if (computedDistance != totalDistance) {
            return false;
        }
        return true;
    }
}