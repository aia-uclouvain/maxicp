package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.DistanceNew;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.cp.CPFactory.makeDfs;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.INSERTABLE;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

// instances from:
// Hybrid Metaheuristic for the Prize Collecting Travelling Salesman Problem
// http://www.lac.inpe.br/~lorena/ (Antonio Augusto Chaves & Luiz Antonio Nogueira Lorena)
public class PCTSP {

    public static void main(String[] args) {

        PCTSP.PCTSPInstance instance = new PCTSP.PCTSPInstance("data/PCTSP/v10.txt");

        // ===================== read & preprocessing =====================

        int n = instance.n;
        int depot = 0; // index of the depot, it must be part of the tour
        // a SeqVar needs both a start and an end node, duplicate the depot
        int[][] distance = new int[n + 1][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(instance.travelCosts[i], 0, distance[i], 0, n);
            distance[i][n] = instance.travelCosts[i][0];
            distance[n][i] = instance.travelCosts[0][i];
        }

        makeTriangularInequality(distance); // ensure triangular inequality

        double sigma = 0.2; // can be 02, 0.5, 0.8
        int minPrize = (int) (sigma * Arrays.stream(instance.prize).sum());

        // ===================== decision variables =====================

        CPSolver cp = makeSolver();
        // route for the traveler
        CPSeqVar tour = makeSeqVar(cp, n + 1, 0, n);
        // distance traveled
        CPIntVar totLength = makeIntVar(cp, 0, 100000);

        // ===================== constraints =====================

        CPBoolVar[] required = makeBoolVarArray(n, node -> tour.isNodeRequired(node));

        CPIntVar[] prize = makeIntVarArray(n, node -> mul(required[node], instance.prize[node]));
        CPIntVar[] penalty = makeIntVarArray(n, node -> mul(CPFactory.not(required[node]), instance.penalty[node]));

        CPIntVar totPrice = sum(prize);
        CPIntVar totPenalty = sum(penalty);

        cp.post(ge(totPrice, minPrize)); // ensure minimum prize collected

//        cp.post(new Distance(tour, distance, totLength)); // [WARNING]: triangular inequality not respected with distance matrix
         cp.post(new DistanceNew(tour, distance, totLength)); // BUGGY, does not work well with optional

        CPIntVar objVar = sum(totPenalty, totLength);

        // minimize tour length + penalties of unvisited nodes
        Objective obj = cp.minimize(objVar);

        // ===================== search =====================

        int[] nodes = new int[n];
        DFSearch dfs = makeDfs(cp,
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

        // ===================== solve the problem =====================

        long init = System.currentTimeMillis();
        dfs.onSolution(() -> {
            double elapsedSeconds = (double) (System.currentTimeMillis() - init) / 1000.0;
            System.out.println("objective:" + objVar);
            System.out.println(String.format("length: %d penalty: %d", totLength.min(), totPenalty.min()));
            System.out.println(tour);
            System.out.println(String.format("totPrize: %d minPrize: %d", totPrice.min(), minPrize));
            System.out.println("number of nodes:" + tour.nNode(SeqStatus.REQUIRED));
            System.out.printf("elapsed time: %.3f%n", elapsedSeconds);
            System.out.println("-------");
        });

        SearchStatistics stats = dfs.optimize(obj);
        double elapsedSeconds = (double) (System.currentTimeMillis() - init) / 1000.0;
        System.out.printf("elapsed - total: %.3f%n", elapsedSeconds);
        System.out.println(stats);
    }

    private static void bellmanFord(int numNodes, int edgeCount, int[][] edges, int src, long[] dist) throws Exception {
        // Initially distance from source to all other vertices
        // is not known(Infinite).
        int INF = Integer.MAX_VALUE;
        Arrays.fill(dist, INF);
        dist[src] = 0;
        // Relaxation of all the edges V times, not (V - 1) as we
        // need one additional relaxation to detect negative cycle
        for (int i = 0; i < numNodes; i++) {
            for (int ne = 0; ne < edgeCount; ne++) {
                int u = edges[ne][0];
                int v = edges[ne][1];
                int wt = edges[ne][2];
                if (dist[u] != INF && dist[u] + wt < dist[v]) {
                    // V_th relaxation => negative cycle
                    if (i == numNodes - 1) {
                        throw new Exception();
                    }
                    // Update shortest distance to node v
                    dist[v] = dist[u] + wt;
                }
            }
        }
    }

    private static void makeTriangularInequality(int[][] distance) {
        int n = distance.length;
        int[][] edges = new int[n * n][3];
        int edgeCount = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                edges[edgeCount][0] = i;
                edges[edgeCount][1] = j;
                edges[edgeCount][2] = distance[i][j];
                edgeCount++;
            }
        }
        for (int i = 0; i < n; i++) {
            long[] dist = new long[n];
            try {
                bellmanFord(n, edgeCount, edges, i, dist);
                for (int j = 0; j < n; j++) {
                    distance[i][j] = (int) dist[j];
                }
            } catch (Exception e) {
                System.out.println("negative cycle");
            }
        }
    }



    public static class PCTSPInstance {

        public int n; // number of nodes
        public int[] prize;
        public int[] penalty;
        public int[][] travelCosts;

        public PCTSPInstance(String filePath) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(filePath));

                String line;
                int currentSection = 0; // 0: nodes, 1: price, 2: penalty, 3: travel costs

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.startsWith("THE PROBLEM HAVE")) {
                        // Extract number of nodes
                        String[] parts = line.split(" ");
                        for (String part : parts) {
                            if (part.matches("\\d+")) {
                                this.n = Integer.parseInt(part);
                                this.prize = new int[this.n];
                                this.penalty = new int[this.n];
                                this.travelCosts = new int[this.n][this.n];
                                break;
                            }
                        }
                    } else if (line.startsWith("PRIZE ASSOCIATED TO EACH NODES")) {
                        currentSection = 1;
                    } else if (line.startsWith("PENALTY ASSOCIATED TO EACH NODES")) {
                        currentSection = 2;
                    } else if (line.startsWith("TRAVEL COST BETWEEN THE NODES")) {
                        currentSection = 3;
                    } else if (currentSection == 1 && !line.startsWith("PRIZE")) {
                        // Parse price line
                        String[] values = line.split("\\s+");
                        for (int i = 0; i < values.length && i < this.n; i++) {
                            if (!values[i].isEmpty()) {
                                this.prize[i] = Integer.parseInt(values[i]);
                            }
                        }
                    } else if (currentSection == 2 && !line.startsWith("PENALTY")) {
                        // Parse penalty line
                        String[] values = line.split("\\s+");
                        for (int i = 0; i < values.length && i < this.n; i++) {
                            if (!values[i].isEmpty()) {
                                this.penalty[i] = Integer.parseInt(values[i]);
                            }
                        }
                    } else if (currentSection == 3 && !line.startsWith("TRAVEL")) {
                        // Parse travel costs line
                        String[] values = line.split("\\s+");
                        int row = Arrays.asList(values).indexOf("0"); // Find the row index (diagonal is 0)
                        if (row >= 0 && row < this.n) {
                            for (int col = 0; col < values.length && col < this.n; col++) {
                                if (!values[col].isEmpty()) {
                                    this.travelCosts[row][col] = Integer.parseInt(values[col]);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

}
