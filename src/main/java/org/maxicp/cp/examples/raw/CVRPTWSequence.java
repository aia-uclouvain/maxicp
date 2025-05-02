/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.engine.constraints.Sum;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.SeqVar;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.util.io.InputReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER;
import static org.maxicp.search.Searches.*;

/**
 * capacitated vehicle routing, with time windows
 */
public class CVRPTWSequence {

    public static void main(String[] args) {
        Instance instance = new Instance("data/CVRPTW/Solomon/C101.txt", 100);
        int nNode = instance.nRequest + 2 * instance.nVehicle;
        int[][] distMatrix = instance.distMatrix;

        // ===================== variables =====================

        CPSolver cp = makeSolver();
        CPSeqVar[] vehicles = new CPSeqVar[instance.nVehicle];
        CPIntVar[] time = new CPIntVar[nNode];
        CPIntVar[] distance = new CPIntVar[instance.nVehicle];
        CPIntVar sumDistance = makeIntVar(cp, 0, instance.nVehicle * instance.depot.twEnd);
        CPIntVar[] vehicleLoad = new CPIntVar[instance.nVehicle];
        int[] duration = new int[nNode];
        int[] load = new int[nNode];
        for (int i = 0; i < instance.nRequest; i++) {
            time[i] = makeIntVar(cp, instance.requestNode[i].twStart, instance.requestNode[i].twEnd);
            duration[i] = instance.requestNode[i].duration;
            load[i] = instance.requestNode[i].demand;
        }
        for (int i = instance.nRequest; i < nNode; i++) {
            time[i] = makeIntVar(cp, instance.depot.twStart, instance.depot.twEnd);
            duration[i] = instance.depot.duration;
            load[i] = instance.depot.demand;
        }
        for (int v = 0; v < instance.nVehicle; v++) {
            vehicles[v] = makeSeqVar(cp, nNode, instance.nRequest + v * 2, instance.nRequest + v * 2 + 1);
            distance[v] = makeIntVar(cp, 0, instance.depot.twEnd);
            vehicleLoad[v] = makeIntVar(cp, 0, instance.capacity);
        }

        // ===================== constraints =====================

        for (int v = 0; v < instance.nVehicle; v++) {
            // each vehicle has a limited capacity
            CPIntVar[] nodeWeight = new CPIntVar[nNode];
            for (int node = 0; node < nNode; node++) {
                // load of each node in the vehicle: 0 if unvisited or its weight
                nodeWeight[node] = mul(vehicles[v].getNodeVar(node).isRequired(), load[node]);
            }
            cp.post(new Sum(nodeWeight, vehicleLoad[v]));
            // visits of nodes must be done within time windows, and transitions are retrieved within a distance matrix
            cp.post(new TransitionTimes(vehicles[v], time, distMatrix, duration));
            // captures the traveled distance
            cp.post(new Distance(vehicles[v], distMatrix, distance[v]));
        }
        // sum all distances into one variable
        cp.post(new Sum(distance, sumDistance));
        // a node is visited exactly once in the problem
        for (int node = 0; node < nNode; node++) {
            CPIntVar[] visits = new CPIntVar[instance.nVehicle];
            for (int vehicle = 0; vehicle < instance.nVehicle; vehicle++) {
                visits[vehicle] = vehicles[vehicle].getNodeVar(node).isRequired();
            }
            cp.post(new Sum(visits, 1));
        }

        // ===================== search =====================

        Integer[] nodesInteger = IntStream.range(0, instance.nRequest).boxed().toArray(Integer[]::new);
        int[] inserts = new int[instance.nRequest];
        Supplier<Runnable[]> branching = () -> {
            if (Arrays.stream(vehicles).allMatch(SeqVar::isFixed))
                return EMPTY; // all vehicles are fixed, a solution has been found
            // select a non-inserted node with the minimum number of insertions across all vehicles
            int node = selectMin(nodesInteger,
                    n -> Arrays.stream(vehicles).anyMatch(route -> route.isNode(n, INSERTABLE)), // select a node that is insertable somewhere
                    n -> Arrays.stream(vehicles).mapToInt(route -> route.nInsert(n)).sum()); // select the node with the min number of insertions
            // given the node, select the insertion with the min detour cost
            int bestCost = Integer.MAX_VALUE;
            int bestPred = 0;
            SeqVar bestRoute = null;
            for (SeqVar seqVar : vehicles) {
                int nInsert = seqVar.fillInsert(node, inserts);
                for (int i = 0 ; i < nInsert ; i++) {
                    int pred = inserts[i];
                    int succ = seqVar.memberAfter(pred);
                    int cost = distMatrix[pred][node] + distMatrix[node][succ] - distMatrix[pred][succ];
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestPred = pred;
                        bestRoute = seqVar;
                    }
                }
            }
            // either insert the node into the sequence using the selected insertion, or remove the selected detour
            SeqVar route = bestRoute;
            int pred = bestPred;
            int succ = route.memberAfter(pred);
            return branch(() -> cp.getModelProxy().add(Factory.insert(route, pred, node)),
                    () -> cp.getModelProxy().add(Factory.notBetween(route, pred, node, succ)));
        };

        // ===================== solve =====================

        DFSearch search = makeDfs(cp, branching);
        Objective distanceObjective = cp.minimize(sumDistance);
        search.onSolution(() -> {
            System.out.printf("Length = %.3f%n", ((double) sumDistance.min()) / Instance.scaling);
            for (int vehicle = 0; vehicle < instance.nVehicle; vehicle++) {
                CPSeqVar path = vehicles[vehicle];
                if (path.nNode(MEMBER) > 2)
                    System.out.println("v" + vehicle + ": " + path.membersOrdered(" -> ",
                            node -> node != path.start() && node != path.end()));
            }
            System.out.println("-----------------");
        });
        search.optimize(distanceObjective);
    }

    static class Instance {

        public static int scaling;
        public int nVehicle;
        public int capacity;
        public int nRequest;  // begin and end nodes are located at nRequest...nRequest+2*nVehicles
        public int[][] distMatrix;
        public String name;
        public CVRPTWNode[] requestNode;
        public CVRPTWNode depot;

        public Instance(String filename, int scaling) {
            Instance.scaling = scaling;
            // name of the instance
            InputReader reader = new InputReader(filename);
            name = reader.getString();
            // next 3 strings are ignored
            for (int i = 0; i < 3; ++i) {
                String line = reader.getString();
            }
            // number of vehicles and capacity
            nVehicle = reader.getInt();
            capacity = reader.getInt();
            // next 12 strings are ignored
            for (int i = 0; i < 12; ++i) {
                String s = reader.getString();
            }
            ArrayList<CVRPTWNode> nodeList = new ArrayList<>();
            try {
                depot = new CVRPTWNode(reader.getInt(), reader.getInt(), reader.getInt(), reader.getInt(),
                        reader.getInt() * scaling, reader.getInt() * scaling, reader.getInt() * scaling);
                while (true) {
                    nodeList.add(new CVRPTWNode(reader.getInt(), reader.getInt(), reader.getInt(), reader.getInt(),
                            reader.getInt() * scaling, reader.getInt() * scaling, reader.getInt() * scaling));
                }
            } catch (RuntimeException ignored) {
                // end of file
            }
            requestNode = nodeList.toArray(new CVRPTWNode[0]);
            nRequest = requestNode.length;
            // compute the distance matrix
            int n = nRequest + 2 * nVehicle;
            distMatrix = new int[n][n];
            for (int i = 0; i < n; ++i) {
                CVRPTWNode from = i < nRequest ? requestNode[i] : depot;
                for (int j = 0; j < n; ++j) {
                    CVRPTWNode to = j < nRequest ? requestNode[j] : depot;
                    distMatrix[i][j] = from.distance(to);
                }
            }
        }

        public record CVRPTWNode(int node, int x, int y, int demand, int twStart, int twEnd, int duration) {

            public int distance(CVRPTWNode o) {
                return (int) Math.floor(Math.sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y)) * Instance.scaling);
            }

        }

    }


}
