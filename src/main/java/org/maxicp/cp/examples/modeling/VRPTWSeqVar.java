package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;
import org.maxicp.util.Arrays;
import org.maxicp.util.DistanceMatrix;
import org.maxicp.util.io.InputReader;

import java.util.List;
import java.util.stream.IntStream;

import static org.maxicp.modeling.Factory.*;

public class VRPTWSeqVar {

    public static void main(String[] args) {
        int nVehicles = 3;
        // use a TSPTW instance and simply puts more vehicles for solving it
        String instancePath = "data/TSPTW/Dumas/n20w20.001.txt";
        TSPTWInstance instance = new TSPTWInstance(instancePath);
        int nCustomers = instance.n;
        // assume that node 0 defines the depot for all vehicles
        // create the starts and ends for the path of the vehicles
        int nNodes = nCustomers + nVehicles * 2;
        int[] start = new int[nVehicles];
        int[] end = new int[nVehicles];
        // every start and end is a duplicate of node 0
        List<Integer> duplicate = IntStream.range(0, nVehicles*2).map(i -> 0).boxed().toList();
        for (int i = 0 ; i < nVehicles ; i++) {
            start[i] = nCustomers + i;
            end[i] = nCustomers + nVehicles + i;
        }
        int[][] distance = DistanceMatrix.extendMatrixAtEnd(instance.distMatrix, duplicate);
        int distanceUpperBound = Arrays.max(instance.distMatrix) * nCustomers;

        // create a model of the problem
        ModelDispatcher baseModel = Factory.makeModelDispatcher();
        // route performed by each vehicle
        SeqVar[] routes = new SeqVar[nVehicles];
        for (int vehicle = 0 ; vehicle < nVehicles ; vehicle++)
            routes[vehicle] = baseModel.seqVar(nNodes, start[vehicle], end[vehicle]);
        // time of visit for each node
        IntExpression[] time = new IntExpression[nNodes];
        for (int customer = 0 ; customer < nCustomers ; customer++)
            time[customer] = baseModel.intVar(instance.earliest[customer], instance.latest[customer]);
        for (int depot = nCustomers ; depot < nNodes ; depot++)
            time[depot] = baseModel.intVar(instance.earliest[0], instance.latest[0]);
        // distance traveled by each vehicle
        IntVar[] vehicleDistance = baseModel.intVarArray(nVehicles, distanceUpperBound);
        IntExpression sumDistances = sum(vehicleDistance);

        // node 0 will be left unused and is thus excluded from all paths
        for (int vehicle = 0 ; vehicle < nVehicles ; vehicle++)
            baseModel.add(exclude(routes[vehicle], 0));
        // each node is visited exactly once
        for (int node = 1 ; node < nNodes ; node++) {
            IntExpression[] visits = new IntExpression[nVehicles];
            for (int vehicle = 0 ; vehicle < nVehicles ; vehicle++)
                visits[vehicle] = routes[vehicle].isNodeRequired(node);
            baseModel.add(eq(sum(visits), 1)); // sum of all visits through the node == 1
        }
        // transition times between the cities for every vehicle
        for (int vehicle = 0 ; vehicle < nVehicles ; vehicle++)
            baseModel.add(transitionTimes(routes[vehicle], time, distance));
        // tracks the traveled distance in each vehicle
        for (int vehicle = 0 ; vehicle < nVehicles ; vehicle++)
            baseModel.add(distance(routes[vehicle], distance, vehicleDistance[vehicle]));

        // objective consists in minimizing the total distance traveled
        Objective minimizeDistance = minimize(sumDistances);

        baseModel.runCP((cp) -> {
            DFSearch search = cp.dfSearch(Searches.firstFailBinary(routes));
            // print each solution found
            search.onSolution(() -> {
                for (int vehicle = 0 ; vehicle < nVehicles ; vehicle++)
                    System.out.println("v" + vehicle + ": " + routes[vehicle]);
                System.out.println(sumDistances);
                System.out.println("------");
            });
            search.optimize(minimizeDistance);; // actually solve the problem
        });

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
                    ", distMatrix=" + java.util.Arrays.deepToString(distMatrix) + "\n" +
                    ", E=" + java.util.Arrays.toString(earliest) + "\n" +
                    ", L=" + java.util.Arrays.toString(latest) + "\n" +
                    ", horizon=" + horizon +
                    '}';
        }
    }

}
