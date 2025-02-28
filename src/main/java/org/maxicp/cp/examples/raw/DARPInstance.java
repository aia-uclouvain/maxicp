/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;


import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.util.io.InputReader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.maxicp.cp.CPFactory.*;

public class DARPInstance {

    final static int SCALING = 100; // scaling to convert distances from float to int
    int nVehicles;                  // number of vehicles allowed
    int nRequests;                  // number of requests to proceed
    int timeHorizon;                // max horizon time considered
    int maxRouteDuration;
    int vehicleCapacity;            // max capacity
    int maxRideTime;                // max time in vehicle for request (service excluded)
    public String filename = "";

    public Stop startDepot;
    public Stop endDepot;
    public Stop[] stops;

    private int pickupFound = 0;
    private int dropFound = 0;

    public DARPSolution solutionNotFound;

    public record Coordinate(double x, double y) {

        public double euclideanDistance(Coordinate o) {
            return euclideanDistanceUnscaled(o) * SCALING;
        }

        public int euclideanDistanceInt(Coordinate o) {
            return (int) Math.round(Math.sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y)) * SCALING);
        }

        public int euclideanDistanceIntCeil(Coordinate o) {
            return (int) Math.ceil(Math.sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y)) * SCALING);
        }

        public int euclideanDistanceIntFloor(Coordinate o) {
            return (int) Math.floor(Math.sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y)) * SCALING);
        }

        public double euclideanDistanceUnscaled(Coordinate o) {
            return Math.sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y));
        }

        public String toString() {
            return String.format("(%+.03f, %+.03f)", x, y);
        }
    }

    public record Stop(Coordinate coord, int servingDuration, int loadChange, int twStart, int twEnd, int id) {

        @Override
        public String toString() {
            return String.format("Stop id= %d (%c) at coord = %s. Tw = [%d, %d]. Load = %d ",
                    id, loadChange > 0 ? 'P' : 'D' ,coord.toString(), twStart, twEnd, loadChange);
        }

    }

    public static int distance(Stop a, Stop b, String rounding) {
        return switch (rounding) {
            case "ceil" -> distanceCeil(a, b);
            case "floor" -> distanceFloor(a, b);
            default -> distance(a, b);
        };
    }

    public static int distance(Coordinate a, Coordinate b, String rounding) {
        return switch (rounding) {
            case "ceil" -> distanceCeil(a, b);
            case "floor" -> distanceFloor(a, b);
            default -> distance(a, b);
        };
    }

    public static int distance(Stop a, Stop b) {
        return distance(a.coord, b.coord);
    }

    public static int distance(Coordinate a, Coordinate b) {
        return a.euclideanDistanceInt(b);
    }

    public static int distanceFloor(Stop a, Stop b) {
        return distanceFloor(a.coord, b.coord);
    }

    public static int distanceFloor(Coordinate a, Coordinate b) {
        return a.euclideanDistanceIntFloor(b);
    }

    public static int distanceCeil(Stop a, Stop b) {
        return distanceCeil(a.coord, b.coord);
    }

    public static int distanceCeil(Coordinate a, Coordinate b) {
        return a.euclideanDistanceIntCeil(b);
    }

    public static double distanceUnscaled(Coordinate a, Coordinate b) { return a.euclideanDistanceUnscaled(b);}

    public static double distanceUnscaled(Stop a, Stop b) {
        return distanceUnscaled(a.coord, b.coord);
    }

    public DARPInstance(String file) {
        filename = file;
        InputReader reader = new InputReader(file);
        nVehicles = reader.getInt();
        nRequests = reader.getInt(); // corresponds to the number of nodes on some instances
        maxRouteDuration = reader.getInt() * SCALING;
        vehicleCapacity = reader.getInt();
        maxRideTime = reader.getInt() * SCALING;
        //stops = new Stop[2*nRequests] ;
        ArrayList<Stop> stopList = new ArrayList<>();
        startDepot = readLine(reader);          // depot start
        // regular pickup/delivery requests
        while (true) {
            //stops[i] = readLine(reader);
            try {
                stopList.add(readLine(reader));
            } catch (RuntimeException e) { // end of file reached
                assert pickupFound == dropFound;
                nRequests = pickupFound;
                break;
            }
        }
        stops = new Stop[2 * nRequests];
        startDepot = new Stop(startDepot.coord, startDepot.servingDuration, startDepot.loadChange, startDepot.twStart, startDepot.twEnd, nRequests);
        endDepot = stopList.get(stopList.size() - 1);
        if (endDepot.loadChange != 0) {
            endDepot = startDepot;
        } else {
            stopList.remove(stopList.size() - 1);
        }
        stopList.toArray(stops);
        //endDepot = readLine(reader);            // depot end
        solutionNotFound = new DARPSolution(new ArrayList<>());
        timeHorizon = Math.max(startDepot.twEnd, Math.max(endDepot.twEnd, Arrays.stream(stops).max(Comparator.comparingInt(i -> i.twEnd)).get().twEnd));
    }

    private DARPInstance(DARPInstance instance) {
        filename = instance.filename;
        nVehicles = instance.nVehicles;
        nRequests = instance.nRequests;
        timeHorizon = instance.timeHorizon;
        vehicleCapacity = instance.vehicleCapacity;
        maxRideTime = instance.maxRideTime;
        startDepot = instance.startDepot;
        stops = instance.stops;
        endDepot = instance.endDepot;
        pickupFound = 0;
        dropFound = 0;
        solutionNotFound = new DARPSolution(new ArrayList<>());
    }

    public DARPInstance copy() {
        return new DARPInstance(this);
    }

    private Stop readLine(InputReader reader) {
        int id = (reader.getInt()-1) % nRequests;
        double x = reader.getDouble();
        double y = reader.getDouble();
        int servingDuration = reader.getInt() * SCALING;
        int loadChange = reader.getInt();
        if (loadChange > 0)  // pickup
            id = pickupFound++;
        else if (loadChange < 0) // drop
            id = dropFound++;
        else // depot
            id = pickupFound;
        return new Stop(new Coordinate(x, y),
                servingDuration,      // serving dur
                loadChange,                // load change
                reader.getInt() * SCALING,      // tw start
                reader.getInt() * SCALING,      // tw end
                id);                            // id of the request
    }

    private Stop readFirstStop(InputReader reader) {
        // depot start
        reader.getInt();
        return new Stop(new Coordinate(reader.getDouble(),reader.getDouble()),
                reader.getInt() * SCALING,     // serving dur
                reader.getInt(),               // load change
                reader.getInt() * SCALING,     // tw start
                reader.getInt() * SCALING,     // tw end
                nRequests);                    // id for the depot
    }

    public DARPSolution constructSolution(List<Stop> stops) {
        DARPSolution sol = new DARPSolution(stops);
        return sol;
    }

    public boolean addRouteFromLine(String line, DARPSolution solution) {
        try {
            int[] succ = new int[nVehicles * 2 + nRequests * 2];
            int i = 0;
            for (String node : line.split(",")) {
                succ[i++] = Integer.parseInt(node.strip());
            }
            int vehicle = 0;
            for (i = nRequests * 2; i < nRequests * 2 + nVehicles; ++i) {
                int next = succ[i];
                while (next < nRequests * 2 + nVehicles) {
                    solution.addStop(vehicle, next);
                    next = succ[next];
                }
                vehicle++;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public boolean addRouteToVehicle(String line, DARPSolution solution) {
        try {
            int vehicle = -1;
            for (String node: line.split(" -> ")) {
                int val = Integer.parseInt(node.strip());
                if (val >= nRequests * 2)
                    vehicle = val - nRequests * 2;
                else
                    solution.addStop(vehicle, val);
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    /**
     * gives the first solution in the file provided
     * @param file where the solution is written, one line per vehicle with " -> " separations
     * @return first solution in the file
     * @throws IOException if the file is not in the right format
     */
    public DARPSolution firstSolutionFromFile(String file) throws IOException {
        ArrayList<Stop> stops = new ArrayList<>(Arrays.asList(this.stops));
        DARPSolution solution = constructSolution(stops);
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line = reader.readLine();
        while (line != null) {
            if (line.contains(" -> ")) {
                if (!addRouteToVehicle(line, solution))
                    throw new IOException("incorrect line in the file");
                if (solution.isCompleted()) {
                    return solution;
                }
            }
            line = reader.readLine();
        }
        throw new IOException("wrong solution format: file ended without completing the solution");
    }

    /**
     * parse solutions from a file containing the list of successors
     * each line should contain all successors for nodes, ordered as in the lnsffpa paper. the successors are separated by commas
     * @param file file where the successors are located
     * @return true if all solutions found are correct
     * @throws IOException
     */
    public boolean checkFromFile(String file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line = reader.readLine();
        boolean correct = true;
        ArrayList<Stop> stops = new ArrayList<>(Arrays.asList(this.stops));
        int i=0;
        int nCorrect = 0;
        int nFailed = 0;
        HashMap<String, DARPSolution> allSolutions = new HashMap<>();
        DARPSolution solution = constructSolution(stops);
        while (line != null) {
            if (line.contains(" -> ")) {
                addRouteToVehicle(line, solution);
                if (solution.isCompleted()) {
                    allSolutions.put(solution.orderedRoute(), solution);
                    boolean okay = solution.exactCheck();
                    correct = correct && okay;
                    String objective;
                    if (okay) {
                        nCorrect += 1;
                        objective = String.format(" objective: %.3f, unscaled: %d",
                                solution.computeNoExcept(true), (int) solution.computeNoExcept(false));
                    } else {
                        nFailed += 1;
                        objective = "";
                    }
                    System.out.println("solution " + ++i + " checked (" + okay + ")" + objective + ". correct: " + nCorrect + " failed: " + nFailed);
                    solution = constructSolution(stops);
                }
            }
            line = reader.readLine();
        }

        System.out.println("correct: " + nCorrect);
        System.out.println("failed: " + nFailed);
        return correct;
    }

    public class DARPSolution implements Comparable<DARPSolution> {

        ArrayList<Integer>[] succ;      // route for each vehicle
        ArrayList<Integer>[] pred;      // route for each vehicle
        private List<Stop> stopsList;      // all stops considered
        private int nbVisited = 0;      // fast check (but imperfect) to see if all routes have been inserted
        private double objective = Double.MAX_VALUE;   // objective value for the solution, expressed as the sum of traveled distances
        private int objectiveScaled = Integer.MAX_VALUE;

        public DARPSolution(List<Stop> stops) {
            this.stopsList = stops;
            reset();
        }

        public DARPSolution copy() {
            DARPSolution clone = new DARPSolution(this.stopsList);
            clone.reset();
            clone.nbVisited = nbVisited;
            clone.objective = objective;
            clone.objectiveScaled = objectiveScaled;
            for (int v = 0 ; v < nVehicles; ++v) {
                clone.succ[v].addAll(succ[v]);
                clone.pred[v].addAll(pred[v]);
            }
            return clone;
        }

        public double getObjective() {
            return objective;
        }

        public int getObjectiveScaled() {
            return objectiveScaled;
        }

        /**
         * compute the objective value of the solution. Throw a runtime exception if the solution cannot happen
         * beware that the time constraints are not correctly checked. False positive can occur
         * if an exact check needs to be performed, use exactCheck instead
         * @return objective value of the solution (unscaled)
         */
        public double compute() {
            double objective = computeNoExcept();
            boolean solved = exactCheck();
            if (!solved)
                throw new RuntimeException();
            return  objective;
        }

        /**
         * compute the objective value without taking a violation of constraint into account
         * @return objective value of the instance (unscaled). Cannot throw an exception
         */
        public double computeNoExcept() {
            if (objective != Double.MAX_VALUE)
                return getObjective();
            return computeNoExcept(true);
        }

        /**
         * compute the objective value without taking a violation of constraint into account
         * @return objective value of the instance (unscaled). Cannot throw an exception
         */
        public double computeNoExcept(boolean unscaled) {
            double objective = 0;
            double currentLength;

            int objectiveScaled = 0;
            int currentLengthScaled = 0;
            // assume that no violation of constraints occur
            Stop begin = startDepot;
            Stop end = endDepot;
            Stop current;
            Stop prev;

            for (int vehicle=0; vehicle < nVehicles; ++vehicle) {
                currentLength = 0;
                currentLengthScaled = 0;
                prev = begin;
                for (int next: succ[vehicle]) {
                    current = stopsList.get(next);
                    currentLength += distanceUnscaled(prev, current);
                    currentLengthScaled += distance(prev, current);
                    //currentLengthScaled += (int) (distanceUnscaled(prev, current) * SCALING);
                    prev = current;
                }
                currentLength += distanceUnscaled(prev, end);
                objective += currentLength;

                currentLengthScaled += distance(prev, end);
                //currentLengthScaled += (int) (distanceUnscaled(prev, end) * SCALING);
                objectiveScaled += currentLengthScaled;
            }
            this.objective = objective;
            this.objectiveScaled = objectiveScaled;
            return unscaled ? objective : objectiveScaled;
        }

        /**
         * naive test to see if the route is completed. Use compute for a more robust test
         * @return true if all requests have been processed
         */
        public boolean isCompleted() {
            return nbVisited == stops.length;
        }

        /**
         * naive test to see if the route is not completed
         * @return true if some requests have not been processed
         */
        public boolean isPartial() {
            return !isCompleted();
        }

        /**
         * Gives the paths in a compact format, that can be represented in one line.
         * First prints the length, then the vehicle
         * Each vehicle entry is represented as "v0: p2 d2 p4 d4 ..."
         * The vehicles are separated by hyphens
         * Example of output:
         *
         * Length: 248.883 - v0: p2 d2 p0 d0 p14 p3 d3 p22 d22 d14 - v1: p8 p16 d8 p7 p19 d16 p6 d19 d7 d6 p9 d9 p1 d1 p12 p15 p17 d12 p18 d17 d15 d18 - v2: p13 p21 p10 d10 d21 d13 p4 d4 p23 d23 p5 p11 d11 d5 p20 d20
         *
         * @return
         */
        public String compactString() {
            StringBuilder b = new StringBuilder();
            b.append(String.format("Length: %.3f - ", computeNoExcept()));
            //int objective = (int) computeNoExcept(false);
            //b.append(String.format("Length: %d - ", objective));
            Stop current;
            for (int vehicle=0; vehicle < nVehicles; ++vehicle) {
                b.append("v");
                b.append(vehicle);
                b.append(": ");
                int i = 1;
                int size = succ[vehicle].size();
                for (int next: succ[vehicle]) {
                    current = stopsList.get(next);
                    if (current.loadChange > 0)
                        b.append('p');
                    else
                        b.append('d');
                    b.append(current.id);
                    if (size != i++)
                        b.append(' ');
                }
                if (vehicle != nVehicles - 1)
                    b.append(" - ");
            }
            return b.toString();
        }
        
        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("Length: ");
            b.append(computeNoExcept());
            b.append("\n");
            Stop current;
            for (int vehicle=0; vehicle < nVehicles; ++vehicle) {
                b.append("vehicle ");
                b.append(vehicle);
                b.append(": ");
                int i = 1;
                int size = succ[vehicle].size();
                for (int next: succ[vehicle]) {
                    current = stopsList.get(next);
                    b.append(current.id);
                    if (current.loadChange > 0)
                        b.append('p');
                    else
                        b.append('d');
                    if (size != i++)
                        b.append(',');
                }
                b.append('\n');
            }
            return b.toString();
        }

        public String detailedRoute() {
            StringBuilder b = new StringBuilder();
            Stop current;
            for (int vehicle=0; vehicle < nVehicles; ++vehicle) {
                b.append(nRequests * 2 + vehicle);
                b.append(" -> ");
                b.append(succ[vehicle].stream().map(i -> String.format("%2d", i)).collect(Collectors.joining(" -> ")));
                b.append(" -> ");
                b.append(nRequests * 2 + vehicle + nVehicles);
                b.append('\n');
            }
            return b.toString();
        }

        /**
         * gives each route used by the vehicle, ordered by the id of the first node encountered
         * each route is split using the \n char
         * @return route for each vehicle, each route[i] having its first visit with id less than route[i+1] first visit id
         */
        public String orderedRoute() {
            return Arrays.stream(succ)
                    .sorted(Comparator.comparing(l -> l.isEmpty() ? Integer.MAX_VALUE : l.get(0)))
                    .reduce("",
                            (i, j) -> i + "\n" + String.join(" -> ", j.stream().map(Object::toString).toList()),
                            (str1, str2) -> str1 + str2);
        }

        public void addStop(int vehicle, int stop) {
            succ[vehicle].add(stop);
            nbVisited++;
        }

        public void reset() {
            succ = new ArrayList[nVehicles];
            pred = new ArrayList[nVehicles];
            for (int i=0; i< nVehicles; ++i) {
                succ[i] = new ArrayList<>();
                pred[i] = new ArrayList<>();
            }
        }

        /**
         * check if the solution is really feasible
         * does not take into account the max ride time between a pickup and a drop
         * @return
         */
        public boolean isValid() {
            HashSet<String> nodeVisited = new HashSet<>();
            for (int vehicle = 0; vehicle < nVehicles ; ++vehicle) {
                int currentTime = 0;
                int capacity = 0;
                Stop pred = startDepot;
                for (int node : succ[vehicle]) {
                    Stop current = stops[node];
                    if (nodeVisited.contains(current.toString()))
                        throw new RuntimeException("node visited twice");
                    nodeVisited.add(current.toString());

                    capacity += current.loadChange;
                    if (capacity > vehicleCapacity)
                        throw new RuntimeException("capacity above max");
                    else if (capacity < 0)
                        throw new RuntimeException("capacity below 0");

                    currentTime = Math.max(currentTime + distance(pred, current), current.twStart);
                    if (currentTime > current.twEnd)
                        throw new RuntimeException("node visited after its time window end");
                    currentTime += current.servingDuration;
                    pred = current;
                }
                if (capacity != 0)
                    throw new RuntimeException("route ended with non zero capacity");
                currentTime = Math.max(currentTime + distance(pred, endDepot), endDepot.twStart);
                if (currentTime > endDepot.twEnd)
                    throw new RuntimeException(String.format("end depot visited after its time window end (visited at %d vs %d)", currentTime, endDepot.twEnd));
            }
            if (nodeVisited.size() != stopsList.size())
                throw new RuntimeException("not all nodes were visited");
            return true;
        }

        /**
         * check if the solution is really feasible. Use constraints to get a real value for the time visited
         */
        public boolean exactCheck() {
            CPSolver cp = makeSolver();
            int numVars = (nRequests + nVehicles) * 2;
            int startDepotIdx = nRequests * 2;
            int endDepotIdx = startDepotIdx + nVehicles;
            CPIntVar[] servingTime = new CPIntVar[numVars];  // serving time of each node
            CPIntVar[] servingVehicle = new CPIntVar[nRequests * 2];  // serving vehicle of each request
            int[] capaAtNode = new int[numVars];
            // order the nodes in order to have pickups in 0...nRequests-1
            // and corresponding drops in nRequests...2*nRequests-1
            Stop[] stops = new Stop[numVars];
            ArrayList<Integer>[] succSol = new ArrayList[nVehicles];
            int[] serviceDuration = new int[numVars];
            for (int i = 0 ; i < nVehicles ; ++i) {
                succSol[i] = new ArrayList<>();
            }
            int[] ordering = new int[startDepotIdx]; // used to remember the mapping of nodes
            int j = 0;
            for (Stop stop: stopsList) { // map the ordering for the stops
                int idx = stop.id + (stop.loadChange > 0 ? 0 : nRequests);
                stops[idx] = stop;
                ordering[j] = idx;
                serviceDuration[idx] = stop.servingDuration;
                ++j;
            }
            // map the ordering for the successors
            int vehicle = 0;
            for (ArrayList<Integer> route: succ) {
                for (int node : route)
                    succSol[vehicle].add(ordering[node]);
                ++vehicle;
            }

            for (int i = 0; i < nRequests * 2 ; ++i) {
                servingTime[i] = makeIntVar(cp, stops[i].twStart, stops[i].twEnd);
                servingVehicle[i] = makeIntVar(cp, 0 , nVehicles);
            }
            for (int i =  startDepotIdx; i < endDepotIdx ; ++i) {
                servingTime[i] = makeIntVar(cp, startDepot.twStart, startDepot.twEnd);
                stops[i] = startDepot;
            }
            for (int i =  endDepotIdx; i < numVars ; ++i) {
                servingTime[i] = makeIntVar(cp, endDepot.twStart, endDepot.twEnd);
                stops[i] = endDepot;
            }
            try {
                for (int i = 0 ; i < nRequests ; ++i) {
                    // constraints over the max ride time
                    try {
                        CPIntVar val = sum(servingTime[i + nRequests], minus(plus(servingTime[i], serviceDuration[i])));
                        cp.post(lessOrEqual(val, maxRideTime));
                    } catch (InconsistencyException e) {
                        //System.err.println("failed to post constraint when initializing the instance (maxRideTime constraint)");
                        throw e;
                    }
                    // pickup and drop are serviced by the same vehicle
                    try {
                        cp.post(equal(servingVehicle[i + nRequests], servingVehicle[i]));
                    } catch (InconsistencyException e) {
                        //System.err.println("failed to post constraint when initializing the instance (pickup and drop served by same vehicle)");
                        throw e;
                    }
                    // pickup before drop
                    int servingDuration = stops[i].servingDuration;
                    int timeToDelivery = distance(stops[i], stops[i+nRequests]);
                    try {
                        cp.post(lessOrEqual(plus(servingTime[i], servingDuration + timeToDelivery),
                                servingTime[i + nRequests]));
                    } catch (InconsistencyException e) {
                        //System.err.println("failed to post constraint when initializing the instance (pickup before drop)");
                        throw e;
                    }
                }

                vehicle = 0;
                int currentCapa = 0;
                for (ArrayList<Integer> route : succSol) {
                    int begin = startDepotIdx + vehicle;
                    int end = begin + nVehicles;
                    int pred = begin;
                    for (int node : route) {
                        try {
                            cp.post(equal(servingVehicle[node], vehicle));
                        } catch (InconsistencyException e) {
                            //System.err.println("failed to set the vehicle for node "+ node);
                            throw e;
                        }
                        int dist = distance(stops[pred], stops[node]) + stops[pred].servingDuration;
                        try {
                            cp.post(lessOrEqual(plus(servingTime[pred], dist), servingTime[node]));
                        } catch (InconsistencyException e) {
                            //System.err.println("failed to insert node "+ node + " because of time violation");
                            throw e;
                        }
                        currentCapa += capaAtNode[node];
                        if (currentCapa > vehicleCapacity || currentCapa < 0) {
                            //System.err.println("capacity violation");
                            throw new InconsistencyException();
                        }
                        pred = node;
                    }
                    int dist = distance(stops[pred], stops[end]) + stops[pred].servingDuration;
                    try {
                        cp.post(lessOrEqual(plus(servingTime[pred], dist) , servingTime[end]));
                        cp.post(lessOrEqual(sum(servingTime[end], minus(servingTime[begin])), timeHorizon));
                    } catch (InconsistencyException e) {
                        //System.err.println("failed to complete the route after insert of the last node for vehicle"+ vehicle);
                        throw e;
                    }
                    ++vehicle;
                }
                for (int i = 0; i < nRequests * 2 ; ++i) {
                    if (!servingVehicle[i].isFixed())
                        return false;
                }
            } catch (InconsistencyException e) {
                return false;
            }
            return true;
        }

        @Override
        public int compareTo(DARPSolution solution) {
            if (this.objective == 0)
                this.computeNoExcept();
            if (solution.getObjective() == 0)
                solution.computeNoExcept();
            return (int) (SCALING * (this.objective - solution.getObjective()));
        }
    }

    /**
     * Enumerates all solutions with a pure and dumb brute force approach
     * This is very slow!
     * @return
     */
    public List<DARPSolution> enumerateBruteForce() {
        // enumerate all solutions and filter out invalid ones
        List<DARPSolution> solutions = new ArrayList<>();
        int nNodesInPerm = nRequests * 2 + nVehicles - 1;
        List<Integer> nodes = IntStream.range(0, nNodesInPerm).boxed().collect(Collectors.toList());
        // loop over the permutations and check how many are valid
        List<List<Integer>> allPermutations = generatePerm(nodes);
        List<Stop> stops = new ArrayList<>(); // no need to specify end nodes, only pickup and drops
        stops.addAll(Arrays.asList(this.stops).subList(0, 2 * this.nRequests));
        int nStops = nRequests * 2;
        for (List<Integer> permutation: allPermutations) {
            DARPSolution solution = new DARPSolution(stops);
            int vehicle = 0;
            for (int node: permutation) {
                if (node >= nStops) {
                    vehicle += 1;
                } else {
                    //int ride = node % nRequests;
                    solution.addStop(vehicle, node);
                }
            }
            try {
                if (solution.exactCheck())
                    solutions.add(solution);
            } catch (RuntimeException ignored) {

            }
        }
        return solutions;
    }

    public static <E> List<List<E>> generatePerm(List<E> original) {
        if (original.isEmpty()) {
            List<List<E>> result = new ArrayList<>();
            result.add(new ArrayList<>());
            return result;
        }
        E firstElement = original.remove(0);
        List<List<E>> returnValue = new ArrayList<>();
        List<List<E>> permutations = generatePerm(original);
        for (List<E> smallerPermutated : permutations) {
            for (int index = 0; index <= smallerPermutated.size(); index++) {
                List<E> temp = new ArrayList<>(smallerPermutated);
                temp.add(index, firstElement);
                returnValue.add(temp);
            }
        }
        return returnValue;
    }

    public static void main(String[] args) throws IOException {
        DARPInstance instance = new DARPInstance("data/DARP/train/train1.txt");
        //instance.checkFromFile("data/darp/solutions_found/scala_lnsffpa/a3-24.txt");
        List<DARPSolution> allSolutions = instance.enumerateBruteForce();
        System.out.printf("%d solutions%n", allSolutions.size());
        for (DARPSolution solution: allSolutions) {
            System.out.println(solution.compactString());
        }
        System.out.printf("%d solutions%n", allSolutions.size());
    }
}
