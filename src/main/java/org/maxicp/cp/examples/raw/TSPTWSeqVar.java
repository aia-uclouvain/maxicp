/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.AllDifferentDC;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.TimeIt;
import org.maxicp.util.algo.DistanceMatrix;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class TSPTWSeqVar {
    public static void main(String[] args) {

        TSPTWInstance instance = new TSPTWInstance("data/TSPTW/Dumas/n40w20.001.txt");

        CPSolver cp = makeSolver();

        CPSeqVar tour = CPFactory.makeSeqVar(cp, instance.n, 0, instance.n-1);

        for (int i = 0; i < instance.n; i++) {
            tour.require(i);
        }

        CPIntVar[] timeWindows = makeIntVarArray(cp, instance.n, instance.horizon);
        CPIntVar totTransition = makeIntVar(cp, 0 , 100000);

        for (int i = 0; i < instance.n; i++) {
            timeWindows[i].removeAbove(instance.latest[i]);
            timeWindows[i].removeBelow(instance.earliest[i]);
        }
        cp.post(new TransitionTimes(tour, timeWindows, instance.distMatrix));

        cp.post(new Distance(tour,instance.distMatrix, totTransition));

        Objective obj = cp.minimize(totTransition);

        // ===================== search =====================

        int[] nodes = new int[instance.n];
        DFSearch dfs = makeDfs(cp,
                // each decision in the search tree will minimize the detour of adding a new node to the path
                () -> {
                    if (tour.isFixed())
                        return EMPTY;
                    // select node with minimum number of insertions points
                    int nUnfixed = tour.fillNode(nodes, INSERTABLE);
                    int node = Searches.selectMin(nodes, nUnfixed, i -> true, tour::nInsert).getAsInt();
                    // get the insertion of the node with the smallest detour cost
                    int nInsert = tour.fillInsert(node, nodes);
                    int bestPred = Searches.selectMin(nodes, nInsert, pred -> true,
                            pred -> {
                                int succ = tour.memberAfter(node);
                                return instance.distMatrix[pred][node] +
                                        instance.distMatrix[node][succ] -
                                        instance.distMatrix[pred][succ];
                            }).getAsInt();
                    // successor of the insertion
                    int succ = tour.memberAfter(bestPred);
                    // either use the insertion to form bestPred -> node -> succ, or remove the detour
                    return branch(() -> cp.getModelProxy().add(Factory.insert(tour, bestPred, node)),
                            () -> cp.getModelProxy().add(Factory.notBetween(tour, bestPred, node, succ)));
                }
        );

        dfs.onSolution(() -> {;
            System.out.println("tour: " + tour);
            System.out.println("total distance: " + totTransition);
        });

        SearchStatistics stats = dfs.optimize(obj);

        System.out.println(stats);

    }

    /**
     * A TSP with Time Windows instance.
     * The depot is duplicated at the end of the instance.
     * So we don't seek for a tour but for a path from the depot to its duplicate at the end
     */
    static class TSPTWInstance {

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

            // dupplicate the depot at the end
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
    }

}

