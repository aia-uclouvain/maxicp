package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;
import org.maxicp.util.Arrays;
import org.maxicp.util.DistanceMatrix;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.maxicp.modeling.Factory.*;

public class PDPSeqVar {

    public static void main(String[] args) {
        // instance generation

        Random random = new Random(42);
        int nVehicles = 3;
        int capacity = 2;
        int nRequests = 10;
        // 2 nodes per trip (pickup and delivery) and 2 per vehicle (start and end)
        int rangeDepot = nRequests * 2;
        int nNodes = nRequests * 2 + nVehicles * 2;
        int[][] originalDistanceMatrix = DistanceMatrix.randomDistanceMatrix(nRequests * 2 + 1, 1000);
        // nodes 0..nRequests are the pickups
        // nodes nRequests..nRequests*2 are the corresponding deliveries
        // nodes nRequests*2..nRequests*2+nVehicles are the start depot
        // nodes nRequests*2+nVehicles..nNodes are the end depot
        List<Integer> duplicate = IntStream.range(0, nVehicles*2).map(i -> rangeDepot).boxed().toList();
        int[][] distance = DistanceMatrix.extendMatrixAtEnd(originalDistanceMatrix, duplicate);
        int[] starts = new int[nRequests];
        int[] ends = new int[nRequests];
        int[] loads = new int[nRequests];
        for (int request = 0 ; request < nRequests ; request++) {
            starts[request] = request;
            ends[request] = request + nRequests;
            loads[request] = 1 + random.nextInt(2); // gives a random load in {1, 2}
        }
        int distanceUpperBound = Arrays.max(distance) * nRequests * 2;

        // model

        ModelDispatcher baseModel = new ModelDispatcher();
        // route performed by each vehicle
        SeqVar[] routes = new SeqVar[nVehicles];
        for (int vehicle = 0 ; vehicle < nVehicles ; vehicle++)
            routes[vehicle] = baseModel.seqVar(nNodes, rangeDepot + vehicle, rangeDepot + nVehicles + vehicle);
        // distance performed by each vehicles
        IntVar[] distances = baseModel.intVarArray(nVehicles, distanceUpperBound);

        // tracks the distance traveled in each vehicle through the distance matrix
        for (int vehicle = 0 ; vehicle < nVehicles ; vehicle++)
            baseModel.add(distance(routes[vehicle], distance, distances[vehicle]));

        // enforce the maximum load in each vehicle, by visiting the pickups and deliveries
        for (int vehicle = 0 ; vehicle < nVehicles ; vehicle++)
            baseModel.add(cumulative(routes[vehicle], starts, ends, loads, capacity));

        // each node is visited exactly once
        for (int node = 1 ; node < nNodes ; node++) {
            IntExpression[] visits = new IntExpression[nVehicles];
            for (int vehicle = 0 ; vehicle < nVehicles ; vehicle++)
                visits[vehicle] = routes[vehicle].isNodeRequired(node);
            baseModel.add(eq(sum(visits), 1)); // sum of all visits through the node == 1
        }

        // total distance is the sum of distances performed by all vehicles
        IntExpression totalDistance = sum(distances);
        // objective is the minimization of total distance
        Objective minimizeDistance = minimize(totalDistance);

        // search
        baseModel.runCP((cp) -> {
            DFSearch search = cp.dfSearch(Searches.firstFail(routes));
            // print each solution found
            search.onSolution(() -> {
                for (int vehicle = 0 ; vehicle < nVehicles ; vehicle++)
                    System.out.println("v" + vehicle + ": " + routes[vehicle]);
                System.out.println(minimizeDistance);
                System.out.println("------");
            });
            search.optimize(minimizeDistance);; // actually solve the problem
        });

    }

}
