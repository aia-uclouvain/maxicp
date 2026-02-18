package org.maxicp.cp.examples.raw.distance;

import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.cp.CPFactory.makeIntVar;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class TSPTWBench extends Benchmark {

    private record TimeWindow(int earliest, int latest) {}

    private static class TSPTWInstance {

        public int nNodes;
        public int start = 0;
        public int[][] travelCosts;
        public TimeWindow[] timeWindows;

        public TSPTWInstance(String instancePath) {
            InputReader reader = new InputReader(instancePath);
            nNodes = reader.getInt();
            travelCosts = new int[nNodes][nNodes];
            for (int i = 0; i < nNodes; i++) {
                for (int j = 0; j < nNodes; j++) {
                    travelCosts[i][j] = reader.getInt();
                }
            }
            timeWindows = new TimeWindow[nNodes];

            for (int i = 0; i < nNodes; i++) {
                int earliest = reader.getInt();
                int latest = reader.getInt();
                timeWindows[i] = new TimeWindow(earliest, latest);
            }
        }

    }

    TSPTWInstance instance;
    CPSolver cp;
    CPSeqVar tour;
    int nNodes;
    int start;
    int end;
    int[][] distance;
    CPIntVar totDistance;
    CPIntVar[] time;

    public TSPTWBench(String[] args) {
        super(args);
    }

    @Override
    protected DFSearch makeDFSearch() {
        int[] nodes = new int[nNodes];
        return makeDfs(cp,
                // each decision in the search tree will minimize the detour of adding a new node to the path
                () -> {
                    if (tour.isFixed())
                        return EMPTY;
                    // select node with minimum number of insertions points.
                    // Ties are broken by selecting the node with smallest id
                    int nUnfixed = tour.fillNode(nodes, INSERTABLE);
                    int node = selectMin(nodes, nUnfixed, i -> true, tour::nInsert).getAsInt();
                    // get the insertion of the node with the smallest detour cost
                    int nInsert = tour.fillInsert(node, nodes);
                    int bestPred = selectMin(nodes, nInsert, pred -> true,
                            pred -> {
                                int succ = tour.memberAfter(node);
                                return distance[pred][node] + distance[node][succ] - distance[pred][succ];
                            }).getAsInt();
                    // successor of the insertion
                    int succ = tour.memberAfter(bestPred);
                    // either use the insertion to form bestPred -> node -> succ, or remove the detour
                    return branch(
                            () -> cp.getModelProxy().add(Factory.insert(tour, bestPred, node)),
                            () -> cp.getModelProxy().add(Factory.notBetween(tour, bestPred, node, succ)));

                }
        );
    }

    @Override
    protected Objective makeObjective() {
        return cp.minimize(totDistance);
    }

    @Override
    protected double getObjectiveValue() {
        return totDistance.min();
    }

    @Override
    protected void makeModel(String instancePath) {

        // ===================== read & preprocessing =====================

        instance = new TSPTWInstance(instancePath);
        start = instance.start;
        nNodes = instance.nNodes;
        end = nNodes;
        // a SeqVar needs both a start and an end node, duplicate the start
        distance = new int[nNodes + 1][nNodes + 1];
        int lengthUpperBound = 0;
        for (int i = 0; i < nNodes; i++) {
            System.arraycopy(instance.travelCosts[i], 0, distance[i], 0, nNodes);
            distance[i][nNodes] = instance.travelCosts[i][0];
            distance[nNodes][i] = instance.travelCosts[0][i];
            lengthUpperBound += Arrays.stream(instance.travelCosts[i]).max().getAsInt();
        }
        makeTriangularInequality(distance); // ensure triangular inequality

        // ===================== decision variables =====================

        cp = makeSolver();
        // route for the traveler
        tour = makeSeqVar(cp, nNodes + 1, start, end);
        // all nodes must be visited
        for (int node = 0 ; node < nNodes ; node++) {
            tour.require(node);
        }
        // distance traveled
        totDistance = makeIntVar(cp, 0, lengthUpperBound);
        // time at which the departure of each node occurs
        time = new CPIntVar[nNodes + 1];
        for (int node = 0; node < nNodes; node++) {
            int earliest = instance.timeWindows[node].earliest;
            int latest = instance.timeWindows[node].latest;
            time[node] = makeIntVar(cp, earliest, latest);
        }
        time[end] = makeIntVar(cp, instance.timeWindows[start].earliest, instance.timeWindows[start].latest);

        // ===================== constraints =====================

        // time windows
        cp.post(new TransitionTimes(tour, time, distance));
        // tracks the distance over the sequence
        addDistanceConstraint(tour, distance, totDistance);
    }

    @Override
    protected CPSeqVar getSeqVar() {
        return tour;
    }

    /**
     * Example of usage:
     * -f "data/TSPTW/Dumas/n60w20.001.txt" -m original
     * @param args
     */
    public static void main(String[] args) {
        new TSPTWBench(args).solve();
    }

}
