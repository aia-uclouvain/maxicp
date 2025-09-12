/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.TimeIt;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;

import static org.maxicp.modeling.Factory.*;

public class TSPTW {

    public static void main(String[] args) {

        TSPTWInstance instance = new TSPTWInstance("data/TSPTW/Dumas/n40w20.001.txt");
        //TSPTWInstance instance = new TSPTWInstance("data/TSPTW/tsptw_10cities_0.txt");

        ModelDispatcher baseModel = makeModelDispatcher();


        IntVar [] arrival = baseModel.intVarArray(instance.n+1, instance.horizon);

        // x[i] is the node visited at position i
        IntVar [] x = baseModel.intVarArray(instance.n+1, instance.n);

        IntExpression [] transition = new IntExpression[instance.n];

        // every node is visited exactly once (except last one)
        baseModel.add(allDifferent(Arrays.copyOf(x,instance.n)));

        // the time starts at 0 in the depot (i.e. node 0)
        baseModel.add(eq(arrival[0],0));
        baseModel.add(eq(x[0], 0));

        // the last node is the depot
        baseModel.add(eq(x[instance.n], 0));

        for (int i = 0; i < instance.n+1; i++) {
            IntExpression earliest = get(instance.earliest, x[i]); // earliest = instance.earliest[x[i]]
            IntExpression latest = get(instance.latest, x[i]); // latest = instance.latest[x[i]]
            baseModel.add(le(arrival[i],latest)); // arrival[i] <= latest[i]
            baseModel.add(le(earliest,arrival[i])); // earliest[i] <= arrival[i]
        }

        for (int i = 0; i < instance.n; i++) {
            transition[i] = get(instance.distMatrix,x[i],x[i+1]); // transition time between x[i] and x[i+1]
            IntExpression arrivalPlusTransition = sum(arrival[i], transition[i]); // arrivalPlusTransition[i] = arrival[i] + transition[i]
            baseModel.add(le(arrivalPlusTransition,arrival[i+1])); // arrivalPlusTransition[i] <= arrival[i+1]
        }

        IntExpression distance = sum(transition);

        long time = TimeIt.run(() -> {
            baseModel.runCP((cp) -> {
                DFSearch search = cp.dfSearch(Searches.firstFail(x));
                search.onSolution(() -> {
                    System.out.println(Arrays.toString(x));
                    System.out.println("solution found distance:"+distance);
                });
                SearchStatistics stats = search.optimize(minimize(distance));
                System.out.println(stats);
            });
        });

        System.out.println("Time (s): " + (time/1000000000.));
    }

    static class TSPTWInstance {

        public final int n;
        public final int[][] distMatrix;
        public final int[] earliest, latest;
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


