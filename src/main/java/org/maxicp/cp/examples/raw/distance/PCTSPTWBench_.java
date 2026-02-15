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
import java.util.Comparator;
import java.util.Random;
import java.util.stream.Collectors;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.Factory.insert;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE_REQUIRED;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class PCTSPTWBench_ extends Benchmark {

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
    double maxMemory = 0.0;

    public PCTSPTWBench_(String[] args) {
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

    /**
     * Branching that alternates between require/exclude operations and insert/notBetween
     * If there is a required insertable node, attempt to insert it or use a notBetween
     * Otherwise, pick the node with the fewest insertions and require it or exclude it
     */
    protected DFSearch makeDFSearch2Stages() {
        int[] nodes = new int[instance.n];
        return makeDfs(cp,
                // each decision in the search tree will minimize the detour of adding a new node to the path
                () -> {
                    if (tour.isFixed())
                        return EMPTY;
                    // select node with minimum number of insertions points.
                    // Ties are broken by selecting the node with smallest id
                    if (tour.nNode(INSERTABLE_REQUIRED) > 0) {
                        // insert the required node having the fewest remaining insertions
                        int nUnfixed = tour.fillNode(nodes, INSERTABLE_REQUIRED);
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
                    } else {
                        // require or exclude the node having the fewest insertions
                        int nUnfixed = tour.fillNode(nodes, INSERTABLE);
                        int node = selectMin(nodes, nUnfixed, i -> true, tour::nInsert).getAsInt();
                        return branch(
                                () -> cp.getModelProxy().add(Factory.require(tour, node)),
                                () -> cp.getModelProxy().add(Factory.exclude(tour, node)));
                    }
                }
        );
    }


    /**
     * Branching that picks the node with the fewest insertions (selecting in priority)
     * and creates 1 branch per insertion point or exclude the node
     */
    protected DFSearch makeDFSearchNaryBranching() {
        int[] nodes = new int[instance.n];
        int nBranchesUpperBound = n + 1;
        int[] insertions = new int[nBranchesUpperBound];
        Runnable[] branches = new Runnable[nBranchesUpperBound];
        Integer[] heuristicVal = new Integer[nBranchesUpperBound];
        Integer[] branchingRange = new Integer[nBranchesUpperBound];
        return makeDfs(cp, () -> {
            if (tour.isFixed())
                return EMPTY;
            int nUnfixed;
            if (tour.nNode(INSERTABLE_REQUIRED) > 0) {
                nUnfixed = tour.fillNode(nodes, INSERTABLE_REQUIRED);
            } else {
                nUnfixed = tour.fillNode(nodes, INSERTABLE);
            }
            int node = selectMin(nodes, nUnfixed, i -> true, tour::nInsert).getAsInt();
            int branch = 0;
            int nInsert = tour.fillInsert(node, insertions);
            for (int j = 0; j < nInsert; j++) {
                int pred = insertions[j]; // predecessor for the node
                int succ = tour.memberAfter(pred);
                branchingRange[branch] = branch;
                heuristicVal[branch] = distance[pred][node] + distance[node][succ] - distance[pred][succ];
                branches[branch++] = () -> tour.getModelProxy().add(insert(tour, pred, node));
            }
            int nBranches = branch;
            Runnable[] branchesSorted = new Runnable[nBranches + 1];
            Arrays.sort(branchingRange, 0, nBranches, Comparator.comparing(j -> heuristicVal[j]));
            for (branch = 0; branch < nBranches; branch++)
                branchesSorted[branch] = branches[branchingRange[branch]];
            branchesSorted[nBranches] = () -> tour.getModelProxy().add(Factory.exclude(tour, node));
            return branchesSorted;
        });
    }

    protected DFSearch makeDFSBinaryBranching() {
        int[] nodes = new int[instance.n];
        return makeDfs(cp,
                // each decision in the search tree will minimize the detour of adding a new node to the path
                () -> {
                    Runtime runtime = Runtime.getRuntime();
                    double allocatedMemory = ((double) runtime.totalMemory()) / (1024*1024);
                    maxMemory = Math.max(maxMemory, allocatedMemory);
                    if (tour.isFixed())
                        return EMPTY;
                    // select node with minimum number of insertions points.
                    // Ties are broken by selecting the node with smallest id
                    // Ties are broken by selecting the node with smallest id
                    int nUnfixed;
                    if (tour.nNode(INSERTABLE_REQUIRED) > 0) {
                        nUnfixed = tour.fillNode(nodes, INSERTABLE_REQUIRED);
                    } else {
                        nUnfixed = tour.fillNode(nodes, INSERTABLE);
                    }
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
    protected DFSearch makeDFSearch() {
        //return makeDFSearch2Stages();
        //return makeDFSearchNaryBranching();
        return makeDFSBinaryBranching();
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

        // set a few nodes as required
        int percentageRequired = 60;
        Random rand = new Random(0);
        for (int node = 0; node < instance.n; node++) {
            if (rand.nextInt(100) < percentageRequired) {
                cp.post(eq(required[node], 1));
            }
        }

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

    @Override
    public String toString() {
        return String.format("%s | %s | %s | %s | %.3f | %.3f | %s | %s | %.3f | %s",
                this.getClass().getSimpleName(),
                instancePath,
                variant,
                bestSolutionString(),
                (double) maxRunTimeMS / 1000.0,
                elapsedSeconds(),
                searchStatsString(),
                solutions.stream().map(CompactSolution::toString).collect(Collectors.joining(" ", "[", "]")),
                maxMemory,
                args);
    }

    /**
     * Example of usage:
     * -f "data/PCTSPTW/toy.txt" -m original
     * @param args
     */
    public static void main(String[] args) {

        new PCTSPTWBench_(args).solve();
    }
}


// choose in priority required nodes
/*
(t=6.533; nodes=398720; fails=199084; obj=252.000)
(t=6.537; nodes=399096; fails=199273; obj=251.000)
(t=6.537; nodes=399118; fails=199283; obj=249.000)
(t=25.496; nodes=1490115; fails=744779; obj=245.000)
(t=25.496; nodes=1490117; fails=744779; obj=243.000)
(t=25.502; nodes=1490490; fails=744966; obj=242.000)
(t=66.564; nodes=3815524; fails=1907484; obj=241.000)
(t=71.737; nodes=4102606; fails=2051024; obj=240.000)
 */