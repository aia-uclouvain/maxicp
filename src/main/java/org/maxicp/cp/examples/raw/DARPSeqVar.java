/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.engine.constraints.LessOrEqual;
import org.maxicp.cp.engine.constraints.Sum;
import org.maxicp.cp.engine.constraints.seqvar.Cumulative;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.io.InputReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.EMPTY;

/**
 * Dial-A-Ride problem
 */
public class DARPSeqVar {

    static class Instance {

        /* ordering of nodes:
        0..nRequest: pickup
        nRequest..nRequest*2: drop
        nRequest*2..nRequest*2+nVehicle: begin depot
        nRequest*2+nVehicle..: ending depot
         */
        static int scaling;
        int nVehicle;
        int nRequest;
        int maxRouteDuration;
        int capacity;
        int maxRideTime;
        DARPNode depot;
        DARPNode[] nodes;
        int[][] distMatrix;

        public Instance(String filename, int scaling) {
            Instance.scaling = scaling;
            InputReader reader = new InputReader(filename);
            nVehicle = reader.getInt();
            reader.getInt(); // ignore, some instances do not use the same format for encoding nodes
            maxRouteDuration = reader.getInt() * scaling;
            capacity = reader.getInt() * scaling;
            maxRideTime = reader.getInt() * scaling;
            reader.getInt(); //id of the node, ignored
            depot = new DARPNode(reader.getDouble(), reader.getDouble(), reader.getInt() * scaling, reader.getInt(),
                    reader.getInt() * scaling, reader.getInt() * scaling);
            ArrayList<DARPNode> nodeList = new ArrayList<>();
            try {
                while (true) {
                    reader.getInt(); // id of the node, ignored
                    nodeList.add(new DARPNode(reader.getDouble(), reader.getDouble(), reader.getInt() * scaling, reader.getInt(),
                            reader.getInt() * scaling, reader.getInt() * scaling));
                }
            } catch (RuntimeException ignored) {

            }
            nodes = nodeList.toArray(new DARPNode[0]);
            nRequest = nodes.length / 2;

            // computeLowerBound the distance matrix
            int n = nVehicle * 2 + nRequest * 2;
            distMatrix = new int[n][n];
            for (int i = 0; i < n; ++i) {
                DARPNode from = i < nRequest * 2 ? nodes[i] : depot;
                for (int j = 0; j < n; ++j) {
                    DARPNode to = j < nRequest * 2 ? nodes[j] : depot;
                    distMatrix[i][j] = from.distance(to);
                }
            }
        }

        public record DARPNode(double x, double y, int duration, int load, int twStart, int twEnd) {

            public int distance(DARPNode o) {
                return (int) Math.round(Math.sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y)) * Instance.scaling);
            }

            public boolean isPickup() {
                return load > 0;
            }

            public boolean isDrop() {
                return load < 0;
            }

            public boolean isDepot() {
                return load == 0;
            }

        }


    }

    public static void main(String[] args) {
        Instance instance = new Instance("data/DARP/Cordeau2003/pr01", 100);
        int n = instance.nRequest * 2 + instance.nVehicle * 2;
        int rangeDepot = instance.nRequest * 2;

        // ===================== decision variables =====================

        CPSolver cp = makeSolver();
        CPSeqVar[] routes = new CPSeqVar[instance.nVehicle];
        CPIntVar[] time = new CPIntVar[n];
        CPIntVar[] distance = new CPIntVar[instance.nVehicle];

        int[] load = new int[instance.nRequest];
        int[] duration = new int[n];
        for (int v = 0; v < instance.nVehicle; v++) { // variables for the sequences, start depot and end depot
            routes[v] = makeSeqVar(cp, n, rangeDepot + v, rangeDepot + instance.nVehicle + v);
            time[rangeDepot + v] = makeIntVar(cp, instance.depot.twStart, instance.depot.twEnd);
            time[rangeDepot + instance.nVehicle + v] = makeIntVar(cp, instance.depot.twStart, instance.depot.twEnd);
            distance[v] = makeIntVar(cp, 0, instance.depot.twEnd);
            duration[rangeDepot + v] = instance.depot.duration;
            duration[rangeDepot + instance.nVehicle + v] = instance.depot.duration;
        }
        for (int i = 0; i < rangeDepot; i++) { // variables for the nodes to visit
            time[i] = makeIntVar(cp, instance.nodes[i].twStart, instance.nodes[i].twEnd);
            duration[i] = instance.nodes[i].duration;
            if (instance.nodes[i].load > 0)
                load[i] = instance.nodes[i].load; // start of an activity (i.e. pickup)
        }

        CPIntVar sumDistance = sum(distance);

        // ===================== constraints =====================

        int[] pickups = IntStream.range(0, instance.nRequest).toArray();
        int[] drops = IntStream.range(instance.nRequest, rangeDepot).toArray();
        for (int v = 0; v < instance.nVehicle; v++) { // variables for the sequences, starting depot and end depot
            // transition time between the nodes
            cp.post(new TransitionTimes(routes[v], time, instance.distMatrix, duration));
            // a vehicle has a limited capacity when visiting pickups and drops
            cp.post(new Cumulative(routes[v], pickups, drops, load, instance.capacity));
            // maximum distance
            cp.post(new Distance(routes[v], instance.distMatrix, distance[v]));
        }
        // max ride time constraint
        for (int i = 0; i < instance.nRequest; i++) {
            // time[drop] <= time[pickup] + duration[pickup] + maxRideTime
            cp.post(new LessOrEqual(time[i + instance.nRequest], plus(time[i], duration[i] + instance.maxRideTime)));
        }
        // some time coherence for the transitions. Not necessary for correctness, but helps for the filtering
        for (int pickup = 0; pickup < instance.nRequest; pickup++) {
            // time[pickup] + duration[pickup] + distance[pickup][drop] <= time[drop]
            int drop = pickup + instance.nRequest;
            cp.post(new LessOrEqual(plus(time[pickup], duration[pickup] + instance.distMatrix[pickup][drop]), time[drop]));
        }
        // max route duration
        for (int i = rangeDepot; i < rangeDepot + instance.nVehicle; i++) {
            // time[end] <= time[start] + maxRouteDuration
            cp.post(new LessOrEqual(time[i + instance.nVehicle], plus(time[i], instance.maxRouteDuration)));
        }
        // a node can be visited once across all routes
        for (int node = 0; node < n; node++) {
            CPIntVar[] visits = new CPIntVar[instance.nVehicle];
            for (int vehicle = 0; vehicle < instance.nVehicle; vehicle++) {
                visits[vehicle] = routes[vehicle].getNodeVar(node).isRequired();
            }
            cp.post(new Sum(visits, 1));
        }

        // ===================== custom search =====================

        int[] insertions = new int[instance.nRequest * 2];

        // used to sort the branches to apply
        Runnable[] branches = new Runnable[n * instance.nVehicle * 2];
        Integer[] heuristicVal = new Integer[n * instance.nVehicle * 2];
        Integer[] branchingRange = new Integer[n * instance.nVehicle * 2];

        // custom search, that inserts the pickup and deliveries as a pair

        Supplier<Runnable[]> branching = () -> {
            // if all routes are fixed, terminates
            if (Arrays.stream(routes).allMatch(CPSeqVar::isFixed))
                return EMPTY;
            // selects the node that can be inserted at the fewest locations
            int bestNode = 0;
            int minInsert = Integer.MAX_VALUE;
            for (int node = 0; node < instance.nRequest * 2; node++) {
                int nInsert = 0;
                boolean required = false;
                for (CPSeqVar route : routes) {
                    // min number of insert for the node
                    nInsert += route.nInsert(node);
                    required = required || route.isNode(node, REQUIRED);
                }
                if (!required)// required nodes have a lower value, and are thus considered first
                    nInsert *= 10000;
                if (nInsert < minInsert && nInsert > 0) { // nInsert > 0 allows to skip nodes already inserted
                    minInsert = nInsert;
                    bestNode = node;
                }
            }
            // insert the node at every feasible insertion across all vehicles
            int node = bestNode;
            int branch = 0;
            for (CPSeqVar route : routes) {
                int nInsert = route.fillInsert(node, insertions);
                for (int j = 0; j < nInsert; j++) {
                    int pred = insertions[j]; // predecessor for the node
                    branchingRange[branch] = branch;
                    heuristicVal[branch] = detourCost(route, instance.distMatrix, pred, node);
                    branches[branch++] = () -> cp.post(insert(route, pred, node));
                }
            }
            int nBranches = branch;
            Runnable[] branchesSorted = new Runnable[nBranches];
            // sort by smallest detour cost
            Arrays.sort(branchingRange, 0, nBranches, Comparator.comparing(j -> heuristicVal[j]));
            for (branch = 0; branch < nBranches; branch++)
                branchesSorted[branch] = branches[branchingRange[branch]];
            return branchesSorted;
        };

        // ===================== solve the problem =====================

        DFSearch search = makeDfs(cp, branching);
        search.onSolution(() -> {
            System.out.printf("length = %.3f%n", ((double) sumDistance.min()) / Instance.scaling);
            for (CPSeqVar route : routes) {
                System.out.println(route.toString());
            }
            System.out.println("-----------------");
        });
        long init = System.currentTimeMillis();
        Objective totalDistance = cp.minimize(sumDistance);
        SearchStatistics stats = search.optimize(totalDistance, searchStatistics -> {
            long elapsed = System.currentTimeMillis() - init;
            return elapsed >= 20_000;
        });
        long elapsed = System.currentTimeMillis() - init;
        System.out.printf("search finished after %.3f [s]", elapsed / 1000.0);
        System.out.println(stats);
    }

    /**
     * evaluation function for inserting two nodes after given predecessor, minimizing the detour
     */
    private static int detourCost(CPSeqVar seq, int[][] d, int pred, int node) {
        int succ = seq.memberAfter(pred);
        return d[pred][node] + d[node][succ] - d[node][succ]; // detour for visiting first node
    }

}
