/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.algo.DistanceMatrix;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

/**
 * Traveling salesman problem with time windows.
 * <p>
 * Instances from: https://lopez-ibanez.eu/tsptw-instances
 */
public class PCTSPTWSeqVar {
    public static void main(String[] args) {

        // In the instance representation, the depot is duplicated at the end of the instance.
        // So we don't seek for a tour but for a path from the depot to its duplicate at the end
        PCTSPTWInstance instance = new PCTSPTWInstance("data/PCTSPTWTest/test.txt");

        CPSolver cp = makeSolver();

        // Sequence variable representing the path from depot to its duplicate
        CPSeqVar tour = CPFactory.makeSeqVar(cp, instance.n, 0, instance.n - 1);

        // Time window vars that represent when nodes are visited
        CPIntVar[] timeWindows = makeIntVarArray(cp, instance.n, instance.horizon);
        cp.post(eq(timeWindows[0], 0)); // start at time 0 in depot
        for (int i = 1; i < instance.n; i++) {
            timeWindows[i].removeAbove(instance.latest[i]);
            timeWindows[i].removeBelow(instance.earliest[i]);
        }

        double sigma = 0.2; // can be 02, 0.5, 0.8
        int minPrize = (int) (sigma * Arrays.stream(instance.prize).sum());

        CPBoolVar[] required = makeBoolVarArray(instance.n, node -> tour.isNodeRequired(node));

        CPIntVar[] prize = makeIntVarArray(instance.n, node -> mul(required[node], instance.prize[node]));
        CPIntVar[] penalty = makeIntVarArray(instance.n, node -> mul(CPFactory.not(required[node]), instance.penalty[node]));

        CPIntVar totPrice = sum(prize);
        CPIntVar totPenalty = sum(penalty);

        // time windows constraints
        cp.post(new TransitionTimes(tour, timeWindows, instance.distMatrix));


        CPIntVar totTransition = makeIntVar(cp, 0, 100000);

        // price collecting constraints
        cp.post(ge(totPrice, minPrize)); // ensure minimum prize collected

        // distance constraints
        cp.post(new Distance(tour, instance.distMatrix, totTransition));

        // minimize tour length + penalties of unvisited nodes
//        CPIntVar objVar = totTransition;
        CPIntVar objVar = sum(totPenalty, totTransition);
        Objective obj = cp.minimize(objVar);

        // ===================== search =====================

        int[] nodes = new int[instance.n];
        DFSearch dfs = makeDfs(cp,
                // each decision in the search tree will minimize the detour of adding a new node to the path
                () -> {
                    if (tour.isFixed())
                        return EMPTY;
                    // Select node with minimum number of insertions points
                    int nUnfixed = tour.fillNode(nodes, INSERTABLE);
                    int node = Searches.selectMin(nodes, nUnfixed, i -> true, tour::nInsert).getAsInt();
                    // Get the insertion of the node with the smallest detour cost
                    int nInsert = tour.fillInsert(node, nodes);
                    int bestPred = Searches.selectMin(nodes, nInsert, pred -> true,
                            pred -> {
                                int succ = tour.memberAfter(node);
                                return instance.distMatrix[pred][node] +
                                        instance.distMatrix[node][succ] -
                                        instance.distMatrix[pred][succ];
                            }).getAsInt();
                    // Successor of the insertion
                    int succ = tour.memberAfter(bestPred);
                    // Either use the insertion to form bestPred -> node -> succ, or remove the detour
                    return branch(() -> cp.getModelProxy().add(Factory.insert(tour, bestPred, node)),
                            () -> cp.getModelProxy().add(Factory.notBetween(tour, bestPred, node, succ)));
                }
        );

        dfs.onSolution(() -> {
            ;
            System.out.println("tour: " + tour);
            System.out.println("total distance + penality: " + totTransition +"+"+ totPenalty +"="+ objVar);
            System.out.println("total prize collected: " + totPrice);
        });

        SearchStatistics stats = dfs.optimize(obj);

        System.out.println(stats);

    }

    /**
     * A TSP with Time Windows instance.
     * The depot is duplicated at the end of the instance.
     * So we don't seek for a tour but for a path
     * from the depot to its duplicate at the end
     */
    static class PCTSPTWInstance {

        public int n;
        public int[][] distMatrix;
        public int[] earliest, latest;
        public int horizon = Integer.MIN_VALUE;
        public int[] prize;
        public int[] penalty;

        public PCTSPTWInstance(String file) {
            InputReader reader = new InputReader(file);
            n = reader.getInt();
            distMatrix = new int[n + 1][n + 1];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    distMatrix[i][j] = reader.getInt();
                }
            }
            for (int j = 0; j < n; j++) {
                distMatrix[n][j] = distMatrix[0][j];
                distMatrix[j][n] = distMatrix[j][0];
            }
            earliest = new int[n + 1];
            latest = new int[n + 1];

            for (int i = 0; i < n; i++) {
                earliest[i] = reader.getInt();
                latest[i] = reader.getInt();
                horizon = Math.max(horizon, latest[i] + 1);
            }

            earliest[n] = earliest[0];
            latest[n] = latest[0];

            prize = new int[n + 1];
            penalty = new int[n + 1];

            for (int i = 0; i < n; i++) {
                prize[i] = reader.getInt();
            }
            prize[n] = prize[0];
            for (int i = 0; i < n; i++) {
                penalty[i] = reader.getInt();
            }
            penalty[n] = penalty[0];

            DistanceMatrix.enforceTriangularInequality(distMatrix);
            n += 1;
        }

        @Override
        public String toString() {
            return "Instance{" +
                    "n=" + n + "\n" +
                    ", distMatrix=" + Arrays.deepToString(distMatrix) + "\n" +
                    ", E=" + Arrays.toString(earliest) + "\n" +
                    ", L=" + Arrays.toString(latest) + "\n" +
                    ", P=" + Arrays.toString(prize) + "\n" +
                    ", Pen=" + Arrays.toString(penalty) + "\n" +
                    ", horizon=" + horizon +
                    '}';
        }
    }

}

