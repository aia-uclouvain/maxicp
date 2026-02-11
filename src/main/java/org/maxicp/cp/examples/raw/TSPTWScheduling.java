/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.*;
import org.maxicp.cp.engine.constraints.scheduling.NoOverlapBinaryWithTransitionTime;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.core.*;
import org.maxicp.cp.examples.raw.amaury.PositionBasedDisjunctive;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.search.*;
import org.maxicp.util.algo.DistanceMatrix;
import org.maxicp.util.io.InputReader;

import java.util.ArrayList;
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

        // channeling between positionOfNode and taskInPosition positionOfNode[i] = p <=> taskInPosition[p] = i
        cp.post(new InversePerm(positionOfNode, taskInPosition));
        cp.post(nonOverlap(visits));

        // enforce the transition time constraints
        ArrayList<IntVar> precedences = new ArrayList<>();
        for (int i = 0; i < instance.n; i++) {
            for (int j = i+1; j < instance.n; j++) {
                CPBoolVar iBeforej = CPFactory.isLe(positionOfNode[i], positionOfNode[j]);
                //CPBoolVar iBeforej = CPFactory.makeBoolVar(cp);

                // (positionOfNode[i] <= positionOfNode[j] -1)  <=> iBeforej = 1
                cp.post(new IsLessOrEqualVar(iBeforej,positionOfNode[i], minus(positionOfNode[j], 1)));


                // (visits[i] << visits[j]) <=> iBeforej = 1
                cp.post(new NoOverlapBinaryWithTransitionTime(iBeforej, visits[i], visits[j], instance.distMatrix[i][j], instance.distMatrix[j][i]));

                precedences.add(iBeforej);
            }
            cp.post(eq(element(taskInPosition,positionOfNode[i]), i));
        }

        cp.post(eq(positionOfNode[0],0)); // start at depot
        cp.post(startAt(visits[0],0 )); // at time 0
        cp.post(eq(positionOfNode[instance.n-1],instance.n-1)); // end at depot duplicate

        // transition times and objective
        CPIntVar totTransition = makeIntVar(cp, 0 , instance.horizon);
        CPIntVar[] transitionTimes = makeIntVarArray(cp, instance.n-1, 0, instance.horizon);
        for (int i = 0; i < instance.n-1; i++) {
            cp.post(eq(transitionTimes[i],
                    CPFactory.element(instance.distMatrix, taskInPosition[i], taskInPosition[i+1])));
        }
        cp.post(sum(transitionTimes, totTransition));

        Objective obj = cp.minimize(totTransition);

        // ===================== search =====================

        // Search that assign the time
        DFSearch dfs = makeDfs(cp,conflictOrderingSearch(precedences.toArray(new CPIntVar[0])));


        dfs.onSolution(() -> {;
            System.out.println("tour: " + Arrays.toString(taskInPosition));
            System.out.println("distances: "+ Arrays.toString(transitionTimes));
            System.out.println("total distance: " + totTransition);
            // check solution
            int[] tour = new int[instance.n];
            for (int i = 0; i < instance.n; i++) {
                tour[i] = taskInPosition[i].min();
            }
            boolean ok = instance.checkSolution(tour, totTransition.min());
            System.out.println("solution valid: " + ok);
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

}

