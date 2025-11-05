package org.maxicp.cp.examples.raw.distance;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.examples.raw.PCTSP;
import org.maxicp.modeling.Factory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.Searches;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.cp.CPFactory.ge;
import static org.maxicp.cp.CPFactory.makeBoolVarArray;
import static org.maxicp.cp.CPFactory.makeIntVar;
import static org.maxicp.cp.CPFactory.makeIntVarArray;
import static org.maxicp.cp.CPFactory.makeSeqVar;
import static org.maxicp.cp.CPFactory.mul;
import static org.maxicp.cp.CPFactory.sum;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class PCTSPBench extends Benchmark {

    PCTSP.PCTSPInstance instance;
    int n;
    int[][] distance;
    CPSolver cp;
    CPSeqVar tour;
    CPIntVar totLength;
    CPBoolVar[] required;
    CPIntVar[] prize;
    CPIntVar[] penalty;
    CPIntVar totPrice;
    CPIntVar totPenalty;
    CPIntVar objVar;

    public PCTSPBench(String[] args) {
        super(args);
    }

    @Override
    protected DFSearch makeDFSearch() {
        int[] nodes = new int[n];
        return makeDfs(cp,
                // each decision in the search tree will minimize the detour of adding a new node to the path
                () -> {
                    if (tour.isFixed())
                        return EMPTY;
                    // select node with minimum number of insertions points
                    int nUnfixed = tour.fillNode(nodes, INSERTABLE);
                    int node = Searches.selectMin(nodes, nUnfixed, i -> true, tour::nInsert).getAsInt();
                    // get the insertion of the node with the smallest detour cost
                    int nInsert = tour.fillInsert(node, nodes);
                    int bestPred = Searches.selectMin(nodes, nInsert, pred -> true,
                            pred -> {
                                int succ = tour.memberAfter(node);
                                return distance[pred][node] + distance[node][succ] - distance[pred][succ];
                            }).getAsInt();
                    // successor of the insertion
                    int succ = tour.memberAfter(bestPred);
                    // either use the insertion to form bestPred -> node -> succ, or remove the detour
                    return branch(
                            () -> cp.getModelProxy().add(Factory.insert(tour, bestPred, node)),
                            () -> cp.getModelProxy().add(Factory.notBetween(tour, bestPred, node, succ)),
                            () -> cp.post(eq(required[node], 0)));
                }
        );
    }

    @Override
    protected Objective makeObjective() {
        return cp.minimize(objVar);
    }

    @Override
    protected double getObjectiveValue() {
        return objVar.min();
    }

    @Override
    protected void makeModel(String instancePath) {
        instance = new PCTSP.PCTSPInstance(instancePath);

        // ===================== read & preprocessing =====================

        n = instance.n;
        int depot = 0; // index of the depot, it must be part of the tour
        // a SeqVar needs both a start and an end node, duplicate the depot
        distance = new int[n + 1][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(instance.travelCosts[i], 0, distance[i], 0, n);
            distance[i][n] = instance.travelCosts[i][0];
            distance[n][i] = instance.travelCosts[0][i];
        }

        makeTriangularInequality(distance); // ensure triangular inequality

        double sigma = 0.2; // can be 02, 0.5, 0.8
        int minPrize = (int) (sigma * Arrays.stream(instance.prize).sum());

        // ===================== decision variables =====================

        cp = makeSolver();
        // route for the traveler
        tour = makeSeqVar(cp, n + 1, 0, n);
        // distance traveled
        totLength = makeIntVar(cp, 0, 100000);

        // ===================== constraints =====================

        required = makeBoolVarArray(n, node -> tour.isNodeRequired(node));

        prize = makeIntVarArray(n, node -> mul(required[node], instance.prize[node]));
        penalty = makeIntVarArray(n, node -> mul(CPFactory.not(required[node]), instance.penalty[node]));

        totPrice = sum(prize);
        totPenalty = sum(penalty);

        cp.post(ge(totPrice, minPrize)); // ensure minimum prize collected

        addDistanceConstraint(tour, distance, totLength);

        objVar = sum(totPenalty, totLength);
    }

    /**
     * Example of usage:
     * -f "data/PCTSP/v10.txt" -m original
     * @param args
     */
    public static void main(String[] args) {
        new PCTSPBench(args).solve();
    }
}
