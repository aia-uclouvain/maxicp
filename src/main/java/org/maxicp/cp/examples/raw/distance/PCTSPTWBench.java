package org.maxicp.cp.examples.raw.distance;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.util.algo.DistanceMatrix;
import org.maxicp.util.io.InputReader;

import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.cp.CPFactory.ge;
import static org.maxicp.cp.CPFactory.makeBoolVarArray;
import static org.maxicp.cp.CPFactory.makeIntVar;
import static org.maxicp.cp.CPFactory.makeIntVarArray;
import static org.maxicp.cp.CPFactory.mul;
import static org.maxicp.cp.CPFactory.sum;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class PCTSPTWBench extends Benchmark {

    PCTSPTWInstance instance;

    // number of nodes in the **instance** (without depot duplication)
    int n;
    int[][] distance;
    CPSolver cp;
    CPSeqVar tour;
    CPIntVar totTransition;
    CPBoolVar[] required;
    CPIntVar[] prize;
    CPIntVar[] time;
    CPIntVar totPrice;
    CPIntVar objVar;

    public PCTSPTWBench(String[] args) {
        super(args);
    }


    /**
     * A PCTSPTW instance. No duplication is performed: this is the raw data.
     */
    static class PCTSPTWInstance {

        public int n;
        public int[][] distMatrix;
        public int[] earliest, latest;
        public int distanceUpperBound;
        public int[] prize;
        public int minimumPrize;

        public PCTSPTWInstance(String file) {
            InputReader reader = new InputReader(file);
            n = reader.getInt();
            minimumPrize = reader.getInt();
            distMatrix = new int[n][n];
            distanceUpperBound = 0;
            for (int i = 0; i < n; i++) {
                int max = 0;
                for (int j = 0; j < n; j++) {
                    distMatrix[i][j] = reader.getInt();
                    max = Math.max(max, distMatrix[i][j]);
                }
                distanceUpperBound += max;
            }
            earliest = new int[n];
            latest = new int[n];

            for (int i = 0; i < n; i++) {
                earliest[i] = reader.getInt();
                latest[i] = reader.getInt();
            }

            prize = new int[n];

            for (int i = 0; i < n; i++) {
                prize[i] = reader.getInt();
            }

            DistanceMatrix.enforceTriangularInequality(distMatrix);
        }

        @Override
        public String toString() {
            return "Instance{" +
                    "n=" + n + "\n" +
                    ", distMatrix=" + Arrays.deepToString(distMatrix) + "\n" +
                    ", E=" + Arrays.toString(earliest) + "\n" +
                    ", L=" + Arrays.toString(latest) + "\n" +
                    ", P=" + Arrays.toString(prize) + "\n" +
                    ", minPrize=" + minimumPrize +
                    '}';
        }
    }


    @Override
    protected DFSearch makeDFSearch() {
        int[] nodes = new int[instance.n];
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
        return cp.minimize(objVar);
    }

    @Override
    protected double getObjectiveValue() {
        return objVar.min();
    }

    @Override
    protected void makeModel(String instancePath) {
        instance = new PCTSPTWInstance(instancePath);

        // ===================== read & preprocessing =====================

        n = instance.n;
        // a SeqVar needs both a start and an end node, duplicate the depot
        distance = new int[n + 1][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(instance.distMatrix[i], 0, distance[i], 0, n);
            distance[i][n] = instance.distMatrix[i][0];
            distance[n][i] = instance.distMatrix[0][i];
        }

        // ===================== decision variables =====================

        cp = makeSolver();
        // Sequence variable representing the path from depot to its duplicate
        tour = CPFactory.makeSeqVar(cp, n + 1, 0, n);
        // distance traveled
        totTransition = makeIntVar(cp, 0, instance.distanceUpperBound);
        // Time window vars that represent when nodes are visited
        time = makeIntVarArray(instance.n + 1, node ->  {
            if (node == tour.end()) {
                return makeIntVar(cp, instance.earliest[tour.start()], instance.latest[tour.start()]);
            } else {
                return makeIntVar(cp, instance.earliest[node], instance.latest[node]);
            }
        });

        // ===================== auxiliary variables =====================

        // view telling if a node is required
        required = makeBoolVarArray(instance.n + 1, node -> tour.isNodeRequired(node));

        // multiplication over required node: the prize associated to the visit of a node (= {0, nodePrize})
        prize = makeIntVarArray(instance.n + 1, node -> {
            if (node == tour.end()) {
                return mul(required[node], instance.prize[tour.start()]);
            } else {
                return mul(required[node], instance.prize[node]);
            }
        });

        // ===================== constraints =====================

        // time windows constraint
        cp.post(new TransitionTimes(tour, time, distance));
        // sum over the collected prizes
        totPrice = sum(prize);
        // ensure minimum prize collected
        cp.post(ge(totPrice, instance.minimumPrize));
        // distance constraints
        addDistanceConstraint(tour, distance, totTransition);

        objVar = totTransition;
    }

    /**
     * Example of usage:
     * -f "data/PCTSPTW/toy.txt" -m original
     * @param args
     */
    public static void main(String[] args) {
        new PCTSPTWBench(args).solve();
    }
}
