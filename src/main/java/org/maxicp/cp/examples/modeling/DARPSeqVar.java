/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.Factory;

import static org.maxicp.modeling.Factory.*;

import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.io.InputReader;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.maxicp.modeling.Factory.makeModelDispatcher;
import static org.maxicp.search.Searches.*;

/**
 * Model for the Dial-A-Ride Problem (DARP) using sequence variables.
 * The DARP is to schedule a fleet of vehicles to fulfill a set of transportation requests,
 * where each request specifies a pickup location and a drop-off location.
 * The objective is to minimize the total distance traveled
 * while respecting various constraints, such as vehicle capacity,
 * time windows, and user ride-time limits.
 *
 * @author Augustin Delecluse and Pierre Schaus
 */
public class DARPSeqVar {

    /**
     * Data related to a node in the DARP problem
     */
    public record NodeData(double x, double y, int duration, int load, int twStart, int twEnd) {
        public int distanceCeil(NodeData o) {
            // ceil distance is useful to enforce triangular inequality
            return (int) Math.ceil(Math.sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y)) * Instance.scaling);
        }
    }

    /**
     * Instance of the DARP problem
     */
    static class Instance {

        // Node id semantics:
        // [0..(nRequest-1)] range of pickup nodes
        // [nRequest..(nRequest*2-1)] range of drop nodes
        // [(nRequest*2)..(nRequest*2+nVehicle-1)] range of begin-depot nodes
        // [(nRequest*2+nVehicle..(nRequest*2+2*nVehicle)-1] range of end-depot nodes

        static int scaling;
        int nVehicle;
        int nRequest;
        int maxRouteDuration;
        int capacity;
        int maxRideTime;
        int[][] distMatrix;
        ArrayList<NodeData> nodeData;

        public Instance(String filename, int scaling) {
            Instance.scaling = scaling;
            InputReader reader = new InputReader(filename);
            nVehicle = reader.getInt();
            reader.getInt(); // ignore, some instances do not use the same format for encoding nodes
            maxRouteDuration = reader.getInt() * scaling;
            capacity = reader.getInt() * scaling;
            maxRideTime = reader.getInt() * scaling;
            reader.getInt(); //id of the node, ignored
            NodeData depot = new NodeData(
                    reader.getDouble(), // x coordinate
                    reader.getDouble(), // y coordinate
                    reader.getInt() * scaling, // duration
                    reader.getInt(), // load
                    reader.getInt() * scaling, // start of the time window
                    reader.getInt() * scaling); // end of the time window
            nodeData = new ArrayList<>();
            try {
                while (true) {
                    reader.getInt(); // id of the node, ignored
                    nodeData.add(new NodeData(reader.getDouble(), reader.getDouble(), reader.getInt() * scaling, reader.getInt(),
                            reader.getInt() * scaling, reader.getInt() * scaling));
                }
            } catch (RuntimeException ignored) {

            }
            nRequest = nodeData.size() / 2;
            // add the depots at the end (2 * nVehicle)
            for (int v = 0; v < nVehicle * 2; v++) {
                nodeData.add(depot);
            }
            // compute the distance matrix
            int n = nVehicle * 2 + nRequest * 2;
            distMatrix = new int[n][n];
            for (int i = 0; i < n; i++) {
                NodeData from = nodeData.get(i); // i < nRequest * 2 ? nodes[i] : depot;
                for (int j = 0; j < n; j++) {
                    NodeData to = nodeData.get(j); //j < nRequest * 2 ? nodes[j] : depot;
                    distMatrix[i][j] = from.distanceCeil(to);
                }
            }
        }

        public NodeData get(int i) {
            return nodeData.get(i);
        }
    }

    public static void main(String[] args) {
        Instance inst = new Instance("data/DARP/Cordeau2003/pr01", 100);
        int n = inst.nRequest * 2 + inst.nVehicle * 2; // the 2 * nVehicle depots are at the end

        // for each vehicle, its start and end depot
        int[] start = IntStream.range(0, inst.nVehicle).map(i -> inst.nRequest * 2 + i).toArray();
        int[] end = Arrays.stream(start).map(i -> i + inst.nVehicle).toArray();

        // ===================== decision variables =====================

        ModelDispatcher model = makeModelDispatcher();

        SeqVar[] routes = new SeqVar[inst.nVehicle]; // the sequence of nodes for each vehicle
        IntExpression[] distance = new IntExpression[inst.nVehicle]; // the distance traveled by each vehicle

        for (int v = 0; v < inst.nVehicle; v++) { // variables for the sequences, start depot and end depot
            routes[v] = model.seqVar(n, start[v], end[v]);
            distance[v] = model.intVar(0, inst.get(end[v]).twEnd);
        }

        IntExpression sumDistance = sum(distance);

        IntExpression[] time = new IntExpression[n]; // the time at which a node is visited
        int[] duration = new int[n]; // the duration of the visit of the nodes
        for (int i = 0; i < n; i++) { // variables for the nodes to visit
            time[i] = model.intVar(inst.get(i).twStart, inst.get(i).twEnd);
            duration[i] = inst.get(i).duration;
        }

        // ===================== constraints =====================

        // for each request, its pickup, its drop and its load
        int[] pickup = IntStream.range(0, inst.nRequest).toArray();
        int[] drop = IntStream.range(inst.nRequest, 2 * inst.nRequest).toArray();
        int[] load = IntStream.range(0, inst.nRequest).map(i -> inst.get(i).load).toArray(); // load of pickup nodes

        for (int v = 0; v < inst.nVehicle; v++) { // variables for the sequences, starting depot and end depot
            // transition time between the nodes
            model.add(transitionTimes(routes[v], time, inst.distMatrix, duration));
            // a vehicle has a limited capacity when visiting pickups and drops
            model.add(cumulative(routes[v], pickup, drop, load, inst.capacity));
            // maximum distance
            model.add(distance(routes[v], inst.distMatrix, distance[v]));
        }
        // max ride time constraint
        for (int r = 0; r < inst.nRequest; r++) {
            // time[drop] <= time[pickup] + duration[pickup] + maxRideTime
            model.add(le(time[drop[r]], plus(time[pickup[r]], duration[pickup[r]] + inst.maxRideTime)));
        }
        // redundant constraints using time coherence for the transitions
        for (int r = 0; r < inst.nRequest; r++) {
            // time[pickup] + duration[pickup] + distance[pickup][drop] <= time[drop]
            model.add(le(plus(time[pickup[r]], duration[pickup[r]] + inst.distMatrix[pickup[r]][drop[r]]), time[drop[r]]));
        }
        // max route duration
        for (int v = 0; v < inst.nVehicle; v++) {
            // time[end] <= time[start] + maxRouteDuration
            model.add(le(time[end[v]], plus(time[start[v]], inst.maxRouteDuration)));
        }
        // each node is visited exactly once across all routes
        IntStream.range(0, n).forEach(i -> {
            // for each vehicle, does it visit the node?
            IntExpression[] visits = model.intVarArray(inst.nVehicle, v -> routes[v].isNodeRequired(i));
            model.add(eq(sum(visits), 1));
        });

        // ===================== custom search =====================
        // custom search, inserting the node with the fewest insertions at the place with the smallest distance increase

        // all nodes that must be considered
        int[] nodes = IntStream.range(0, inst.nRequest * 2).toArray();

        // given a node, generate a branch for each insertion in all the routes sorted by the smallest distance detour
        Function<Integer, Runnable[]> branchGenerator = branchesInsertingNode(routes, inst.distMatrix).get();
        // the actual branching
        Supplier<Runnable[]> branching = () -> {
            // select the node with the fewest number of insertions (first fail)
            OptionalInt nodeToInsert = nodeSelector(routes, nodes, (seqvar, node) -> seqvar.nInsert(node));
            if (nodeToInsert.isEmpty()) {
                return EMPTY; // no node to insert -> solution found
            } else {
                int node = nodeToInsert.getAsInt();
                // generate all branches inserting the node, trying first the ones with the smallest detour cost
                return branchGenerator.apply(node);
            }
        };

        // ===================== solve the problem =====================

        ConcreteCPModel cp = model.cpInstantiate();
        DFSearch search = cp.dfSearch(branching);
        search.onSolution(() -> {
            System.out.printf("length = %.3f%n", ((double) sumDistance.min()) / Instance.scaling);
            for (SeqVar route : routes) {
                System.out.println(route.toString());
            }
            System.out.println("-----------------");
        });
        long init = System.currentTimeMillis();
        Objective totalDistance = cp.minimize(sumDistance);
        System.out.println("optimize");
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
    private static int detourCost(SeqVar seq, int[][] d, int pred, int node) {
        int succ = seq.memberAfter(pred);
        return d[pred][node] + d[node][succ] - d[node][succ]; // detour for visiting first node
    }

}
