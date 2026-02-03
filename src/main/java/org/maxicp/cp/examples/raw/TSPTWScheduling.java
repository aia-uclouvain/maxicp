/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.Element1DVar;
import org.maxicp.cp.engine.constraints.scheduling.NoOverlapBinaryWithTransitionTime;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.examples.raw.amaury.PositionBasedDisjunctive;
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
import static org.maxicp.search.Searches.*;

public class TSPTWScheduling {
    public static void main(String[] args) {

        TSPTWInstance instance = new TSPTWInstance("data/TSPTW/Dumas/n40w20.001.txt");

        CPSolver cp = makeSolver();

        // create visits as interval variables of length 0 with time windows
        CPIntervalVar [] visits = CPFactory.makeIntervalVarArray(cp, instance.n);
        for (int i = 0; i < instance.n; i++) {
            visits[i] = makeIntervalVar(cp, false, 0, 0); // 0 length interval
            visits[i].setStartMin(instance.earliest[i]);
            visits[i].setEndMax(instance.latest[i]);
        }

        // noOverlap with transition times
        for (int i = 0; i < instance.n-1; i++) {
            for (int j = i+1; j < instance.n; j++) {
                cp.post(new NoOverlapBinaryWithTransitionTime(visits[i], visits[j], instance.distMatrix[i][j], instance.distMatrix[j][i]));
            }
        }

        // positionOfNode[i] is the position of node i in the tour
        CPIntVar [] positionOfNode = CPFactory.makeIntVarArray(cp, instance.n, instance.n);
        // taskInPosition[i] is the node at position i in the tour
        CPIntVar [] taskInPosition = CPFactory.makeIntVarArray(cp, instance.n, instance.n);

        cp.post(allDifferent(taskInPosition));

        // link disjunctive constraint and position-based representation
        cp.post(new PositionBasedDisjunctive(positionOfNode, visits));

        cp.post(eq(positionOfNode[0],0)); // start at depot
        cp.post(eq(taskInPosition[0],0));

        cp.post(eq(positionOfNode[instance.n-1],instance.n-1)); // end at depot duplicate
        cp.post(eq(taskInPosition[instance.n-1],instance.n-1));

        // transition times and objective
        CPIntVar totTransition = makeIntVar(cp, 0 , 100000);
        CPIntVar[] transitionTimes = makeIntVarArray(cp, instance.n-1, 0, 10000);
        for (int i = 0; i < instance.n-1; i++) {
            cp.post(eq(transitionTimes[i],
                    CPFactory.element(instance.distMatrix, taskInPosition[i], taskInPosition[i+1])));
        }
        cp.post(sum(transitionTimes, totTransition));

        // link positionOfNode and taskInPosition
        for (int i = 0; i < instance.n; i++) {
            cp.post(eq(element(taskInPosition,positionOfNode[i]), i));
        }

        Objective obj = cp.minimize(totTransition);

        // ===================== search =====================

        // DFSearch dfs = makeDfs(cp,firstFail(taskInPosition));
        DFSearch dfs = makeDfs(cp,setTimes(visits));

        dfs.onSolution(() -> {;
            System.out.println("tour: " + Arrays.toString(taskInPosition));
            System.out.println("distances: "+ Arrays.toString(transitionTimes));
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

