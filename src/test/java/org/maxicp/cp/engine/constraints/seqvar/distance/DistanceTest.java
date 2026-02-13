package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

public abstract class DistanceTest extends CPSolverTest {

    static int nNodes = 6;
    static int start = 4;
    static int end = 5;
    static int[][] transitions = new int[][]{
            {0, 3, 5, 4, 4, 4},
            {3, 0, 4, 5, 5, 5},
            {5, 4, 0, 3, 9, 9},
            {4, 5, 3, 0, 8, 8},
            {4, 5, 9, 8, 0, 0},
            {4, 5, 9, 8, 0, 0}
    };

    /**
     * @return i!
     */
    private static int fact(int i) {
        if (i <= 1) return 1;
        return i * fact(i - 1);
    }

    /**
     * Generates a random distance matrix over {@code nNodes} nodes.
     * The matrix upholds the triangular inequality
     *
     * @param random rng used to generate the matrix
     * @param nNodes number of nodes to include
     * @return random distance matrix
     */
    public static int[][] randomTransitions(Random random, int nNodes) {
        int[][] positions = new int[nNodes][];
        for (int i = 0; i < nNodes; i++) {
            int x = random.nextInt(100);
            int y = random.nextInt(100);
            positions[i] = new int[]{x, y};
        }
        return positionToDistances(positions);
    }

    /**
     * Transform a list of coordinates into a matrix of Euclidean distances (rounded up)
     *
     * @param pos list of coordinates
     * @return distance matrix
     */
    public static int[][] positionToDistances(int[][] pos) {
        int n = pos.length;
        int[][] transitions = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                // euclidean distance, rounded up
                int d = roundedEuclideanDistance(pos[i][0], pos[i][1], pos[j][0], pos[j][1]);
                transitions[i][j] = d;
                transitions[j][i] = d;
            }
        }
        return transitions;
    }

    /**
     * Returns the Euclidean distance between two points, rounded up
     *
     * @param x1 x coordinate of the first point
     * @param y1 y coordinate of the first point
     * @param x2 x coordinate of the second point
     * @param y2 y coordinate of the second point
     * @return Euclidean distance, rounded up
     */
    public static int roundedEuclideanDistance(int x1, int y1, int x2, int y2) {
        return (int) Math.ceil(Math.sqrt(Math.pow(Math.abs(x1 - x2), 2) + Math.pow(Math.abs(y1 - y2), 2)));
    }

    /**
     * Stream of sequence variables with all nodes being required
     */
    public static Stream<CPSeqVar> getSeqVar() {
        return getSolver().map(cp -> {
            CPSeqVar seqVar = CPFactory.makeSeqVar(cp, nNodes, start, end);
            for (int node = 0; node < nNodes; node++)
                seqVar.require(node);
            return seqVar;
        });
    }

    protected abstract CPConstraint getDistanceConstraint(CPSeqVar seqVar, int[][] transitions, CPIntVar distance);

    /**
     * Ensures that all solutions to a small instance may be retrieved
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFindAllSolutions1(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        CPIntVar dist = CPFactory.makeIntVar(cp, 0, 100);
        cp.post(getDistanceConstraint(seqVar, transitions, dist));
        // number of sequences that can be constructed: permutation of nNodes - 2 elements
        int nSolutionsExpected = fact(nNodes - 2);
        DFSearch search = makeDfs(cp, firstFail(seqVar));
        int[] nodes = new int[nNodes];
        search.onSolution(() -> {
            assertTrue(dist.isFixed(), "Distance should be fixed when the sequence is fixed");
            int d = 0;
            int nVisited = seqVar.fillNode(nodes, SeqStatus.MEMBER_ORDERED);
            assertEquals(nNodes, nVisited);
            for (int i = 0; i < nNodes - 1; i++) {
                d += transitions[nodes[i]][nodes[i + 1]];
            }
            assertEquals(d, dist.min(), "Distance variable and distance computed do not match");
        });
        SearchStatistics statistics = search.solve();
        assertEquals(nSolutionsExpected, statistics.numberOfSolutions());
    }

    /**
     * Ensures that all solutions to several small instances may be retrieved
     *
     * @param nNodes number of nodes in the sequence
     * @param seed   seed used for random number generation
     */
    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            nNodes, seed
            6,     1
            7,     2
            8,     3
            9,     4
            10,     42
            """)
    public void testFindAllSolutions2(int nNodes, int seed) {
        // generates a random distance matrix
        Random random = new Random(seed);
        int[][] transitions = randomTransitions(random, nNodes);
        // model
        CPSolver cp = makeSolver();
        CPSeqVar seqVar = CPFactory.makeSeqVar(cp, nNodes, nNodes - 2, nNodes - 1);
        for (int node = 0; node < nNodes; node++)
            seqVar.require(node);
        // rough upper bound on the maximum travel distance
        int roughUpperBound = Arrays.stream(transitions).mapToInt(arr -> Arrays.stream(arr).max().getAsInt()).sum();
        CPIntVar distance = CPFactory.makeIntVar(cp, 0, roughUpperBound);
        cp.post(getDistanceConstraint(seqVar, transitions, distance));

        // number of sequences that can be constructed: permutation of nNodes - 2 elements
        int nSolutionsExpected = fact(nNodes - 2);

        DFSearch search = makeDfs(cp, firstFail(seqVar));
        int[] nodes = new int[nNodes];
        search.onSolution(() -> {
            assertTrue(distance.isFixed(), "Distance should be fixed when the sequence is fixed");
            int d = 0;
            int nVisited = seqVar.fillNode(nodes, SeqStatus.MEMBER_ORDERED);
            assertEquals(nNodes, nVisited);
            for (int i = 0; i < nNodes - 1; i++) {
                d += transitions[nodes[i]][nodes[i + 1]];
            }
            assertEquals(d, distance.min(), "Distance variable and distance computed do not match");
        });
        SearchStatistics statistics = search.solve();
        assertEquals(nSolutionsExpected, statistics.numberOfSolutions());
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            nNodes, seed
            5,     1
            5,     2
            5,     3
            5,     4
            5,     5
            5,     6
            5,     7
            5,     8
            5,     9
            5,     10
            5,     11
            5,     12
            5,     13
            5,     14
            5,     15
            5,     16
            5,     17
            5,     18
            5,     19
            5,     20
            6,     1
            6,     2
            6,     3
            6,     4
            6,     5
            6,     6
            6,     7
            6,     8
            6,     9
            6,     10
            8,     1
            8,     2
            8,     3
            8,     4
            8,     5
            8,     6
            8,     7
            8,     8
            8,     9
            8,     10
            9,     1
            10,    1
            15,    1
            15,    2
            15,    3
            15,    4
            """)
    public void testNotRemoveBestSolution(int nNodes, int seed) {
        // generates a random distance matrix
        Random random = new Random(seed);
        int[][] transitions = randomTransitions(random, nNodes);
        // model
        CPSolver cp = makeSolver();
        CPSeqVar seqVar = CPFactory.makeSeqVar(cp, nNodes, nNodes - 2, nNodes - 1);
        for (int node = 0; node < nNodes; node++)
            seqVar.require(node);
        // rough upper bound on the maximum travel distance
        int roughUpperBound = Arrays.stream(transitions).mapToInt(arr -> Arrays.stream(arr).max().getAsInt()).sum();
        CPIntVar distance = CPFactory.makeIntVar(cp, 0, roughUpperBound);

        CostAndSequence best = bestCostFor(seqVar, transitions, roughUpperBound);

        cp.post(getDistanceConstraint(seqVar, transitions, distance));
        //assertTrue(distance.min() <= best, "Initial pruning of the distance overestimated the lower bound");

        // computes the best cost at every node in the search tree and checks if it can be obtained
        // (this is quite slow ;-) )
        Supplier<Runnable[]> checker = () -> {
            CostAndSequence costAndSequence = bestCostFor(seqVar, transitions, roughUpperBound);
            int bestCost = costAndSequence.cost;
            int lb = distance.min();
            if (lb > bestCost) {
                String bestSequenceString = String.join(" -> ", Arrays.stream(costAndSequence.sequence).mapToObj(String::valueOf).toArray(String[]::new));
                String message = "Lower bound is overestimating the best cost.\n" +
                        "Best cost is   " + bestCost + " (with sequence = " + bestSequenceString + ")\n" +
                        "Lower bound is " + lb + "\n" +
                        "Sequence = (copy and paste on https://dreampuf.github.io/GraphvizOnline for visualisation)\n" +
                        seqVar.toGraphViz((pred, node) -> String.valueOf(transitions[pred][node]));
                //distanceNew.propagate();
                fail(message);
            }
            return EMPTY;
        };
        Objective travelCost = cp.minimize(distance);
        DFSearch search = makeDfs(cp, and(checker, minDetourSearchProcedure(seqVar, transitions).get()));
        AtomicInteger bestCostFound = new AtomicInteger(Integer.MAX_VALUE);
        search.onSolution(() -> bestCostFound.set(distance.min()));
        SearchStatistics stats = search.optimize(travelCost);
        assertEquals(best.cost, bestCostFound.get());
    }

    /**
     * Ensures that some trivial inconsistent cases, where the sequence cannot match the distance, are detected
     */
    @Test
    public void testDetectInfeasibility() {
        CPSolver cp = makeSolver();
        CPSeqVar seqVar = CPFactory.makeSeqVar(cp, nNodes, start, end);
        for (int node = 0; node < nNodes; node++)
            seqVar.require(node);
        seqVar.insert(4, 0);
        seqVar.insert(0, 1);
        seqVar.notBetween(1, 2, 5);
        seqVar.notBetween(4, 3, 0);
        // sequence: 4 -> 0 -> 1 -> 5
        // remaining nodes: 2 and 3
        // 2 can be inserted at 4 -> 0 or 0 -> 1
        // 3 can be inserted at 0 -> 1 or 1 -> 5
        // inserting one node could work but both does not due to maximum distance
        int[][] pos = new int[][]{
                {10, 0},
                {20, 0},
                {10, 10}, // to insert
                {20, 10}, // to insert
                {0, 0}, // start
                {30, 0}, // end
        };
        int[][] transitions = positionToDistances(pos);
        CPIntVar dist = CPFactory.makeIntVar(cp, 0, 45);
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(getDistanceConstraint(seqVar, transitions, dist)));
    }

    /**
     * Test on small TSP instances that the search using bounds explore less search nodes than without using bounds
     */
    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            nNodes, seed
                7, 1
                7, 2
                7, 3
                7, 4
                7, 5
                7, 6
                7, 7
                7, 8
                10, 1
                10, 3
                10, 4
                10, 5
                25,     0
                25,     1
                25,     2
            """)
    public void testTSPLessSearchNodesUsingBound(int nNodes, int seed) {
        // instance data
        Random random = new Random(seed);
        int[][] transitions = randomTransitions(random, nNodes);
        int roughUpperBound = Arrays.stream(transitions).mapToInt(arr -> Arrays.stream(arr).max().getAsInt()).sum();
        // model
        CPSolver cp = makeSolver();
        CPSeqVar seqVar = CPFactory.makeSeqVar(cp, nNodes, nNodes - 2, nNodes - 1);
        for (int node = 0; node < nNodes; node++)
            seqVar.require(node);
        CPIntVar distance = CPFactory.makeIntVar(cp, 0, roughUpperBound);

        // search without using bound computation
        cp.getStateManager().saveState();
        StatsAndSolution resultsNoBounds = searchWith(seqVar, distance, transitions,
                () -> cp.post(new Distance(seqVar, transitions, distance)));
        cp.getStateManager().restoreState();

        // search using bound computation
        StatsAndSolution resultsWithBounds = searchWith(seqVar, distance, transitions,
                () -> cp.post(getDistanceConstraint(seqVar, transitions, distance)));
        // compare the 2 searches

        assertEquals(resultsNoBounds.cost, resultsWithBounds.cost, "The optimal solutions must be the same no matter the constraint used");
        assertTrue(resultsWithBounds.stats.numberOfNodes() <= resultsNoBounds.stats.numberOfNodes(),
                "The search should explore less nodes when using bound computation (in optimization)");
        System.out.println("  bounds: " + resultsWithBounds.stats.numberOfNodes() + " nodes\n" +
                "noBounds: " + resultsNoBounds.stats.numberOfNodes() + " nodes");
    }

    /**
     * Gives the minimum distance cost for a given sequence variable.
     * This works by creating a copy of the sequence and computing the best solution through a DFS - so it's very slow
     *
     * @param seqVar sequence on which the minimum cost must be computed
     * @param dist   transition cost between nodes
     * @return best transition cost between nodes
     */
    private CostAndSequence bestCostFor(CPSeqVar seqVar, int[][] dist, int roughUpperBound) {
        seqVar.getSolver().getStateManager().saveState();
        CPSeqVar copy = deepCopy(seqVar);
        CPIntVar distance = CPFactory.makeIntVar(copy.getSolver(), 0, roughUpperBound);
        copy.getSolver().post(new Distance(copy, dist, distance));
        DFSearch search = minDetourSearch(copy, dist);
        AtomicInteger bestCost = new AtomicInteger(Integer.MAX_VALUE);
        int[] sequence = new int[seqVar.nNode()];
        AtomicInteger nMember = new AtomicInteger();
        search.onSolution(() -> {
            nMember.set(copy.fillNode(sequence, SeqStatus.MEMBER_ORDERED));
            int cost = 0;
            for (int i = 0; i < nMember.get() - 1; i++) {
                cost += dist[sequence[i]][sequence[i + 1]];
            }
            bestCost.set(cost);
        });
        search.optimize(seqVar.getSolver().minimize(distance));
        seqVar.getSolver().getStateManager().restoreState();
        int[] bestSolution;
        if (nMember.get() != sequence.length) {
            bestSolution = new int[nMember.get()];
            System.arraycopy(sequence, 0, bestSolution, 0, nMember.get());
        } else {
            bestSolution = sequence;
        }
        return new CostAndSequence(bestCost.get(), bestSolution);
    }

    private CPSeqVar deepCopy(CPSeqVar seqVar) {
        CPSeqVar copy = CPFactory.makeSeqVar(seqVar.getSolver(), seqVar.nNode(), seqVar.start(), seqVar.end());
        int[] nodes = new int[seqVar.nNode()];
        // enforces the current ordering
        int nMember = seqVar.fillNode(nodes, SeqStatus.MEMBER_ORDERED);
        for (int i = 1; i < nMember - 1; i++)
            copy.insert(nodes[i - 1], nodes[i]);
        // requires nodes
        int nRequired = seqVar.fillNode(nodes, SeqStatus.REQUIRED);
        for (int i = 0; i < nRequired; i++)
            copy.require(nodes[i]);
        // exclude nodes
        int nExcluded = seqVar.fillNode(nodes, SeqStatus.EXCLUDED);
        for (int i = 0; i < nExcluded; i++)
            copy.exclude(nodes[i]);
        // add notBetween's
        int nInsertable = seqVar.fillNode(nodes, SeqStatus.INSERTABLE);
        for (int i = 0; i < nInsertable; i++) {
            int node = nodes[i];
            for (int pred = seqVar.start(); pred != seqVar.end(); pred = seqVar.memberAfter(pred)) {
                if (!seqVar.hasInsert(pred, node)) {
                    copy.notBetween(pred, node, seqVar.memberAfter(pred));
                }
            }
        }
        return copy;
    }

    /**
     * Simple test instance to see if the lower bound is not overestimated
     */
    @Test
    public void testNoLowerBoundOverestimation() {
        CPSolver cp = makeSolver();
        int nNodes = 5;
        CPSeqVar seqVar = makeSeqVar(cp, nNodes, 0, 2);
        for (int n = 0; n < nNodes; n++) {
            seqVar.require(n);
        }
        /*
         * representation (more or less)
         *
         *           3     4
         *
         * 0 -- 1 ------------- 2
         */
        int[][] positions = new int[][]{
                {0, 0}, // start
                {0, 10}, // first visit
                {0, 40}, // end
                {10, 20}, // to insert
                {10, 30}, // to insert
        };
        int[][] distMatrix = positionToDistances(positions);
        // sequence: 0 -> 1 -> 2
        // must insert nodes 3 and 4
        // the best sequence is 0 -> 1 -> 3 -> 4 -> 2
        int[] bestPath = new int[]{0, 1, 3, 4, 2};
        int bestCost = 0;
        for (int i = 0; i < nNodes - 1; i++)
            bestCost += distMatrix[bestPath[i]][bestPath[i + 1]];
        CPIntVar distance = CPFactory.makeIntVar(cp, 0, bestCost * 10);
        cp.post(getDistanceConstraint(seqVar, distMatrix, distance));
        assertTrue(distance.min() <= bestCost,
                "The best solution is " + bestCost + " but the lower bound was " + distance.min());
    }

    @Test
    public void testTSPTW() {
        int iter = 89;
        Random rand = new Random(iter);

        int n = 5;

        int[][] dist = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    dist[i][j] = rand.nextInt(100);
                }
            }
        }
        makeTriangularInequality(dist);

        CPSolver cp = makeSolver();
        // route for the traveler
        CPSeqVar tour = makeSeqVar(cp, n, 0, n - 1);
        // all nodes must be visited
        for (int node = 0; node < n; node++) {
//                if (node != 2) // one optional node
            tour.require(node);
        }
        // distance traveled
        CPIntVar totDistance = makeIntVar(cp, 0, n * 100);
        int[] tMin = new int[n];
        int[] tMax = new int[n];
        for (int i = 0; i < n; i++) {
            tMin[i] = tMax[i] = i * 100;
        }
        for (int i = 1; i < n; i++) {
            tMin[i] = Math.max(0, tMin[i] - rand.nextInt(200));
            tMax[i] = tMax[i] + rand.nextInt(200);
        }

        // time at which the departure of each node occurs
        CPIntVar[] time = new CPIntVar[n];
        for (int i = 0; i < n; i++) {
            time[i] = makeIntVar(cp, tMin[i], tMax[i]);
        }

        // time windows
        cp.post(new TransitionTimes(tour, time, dist));
        // tracks the distance over the sequence

        // search without using bound computation
        cp.getStateManager().saveState();
        StatsAndSolution resultsNoBounds = searchWith(tour, totDistance, dist,
                () -> cp.post(new Distance(tour, dist, totDistance)));
        cp.getStateManager().restoreState();

        // search using bound computation
        StatsAndSolution resultsWithBounds = searchWith(tour, totDistance, dist,
                () -> cp.post(getDistanceConstraint(tour, dist, totDistance)));
        // compare the 2 searches

        assertEquals(resultsNoBounds.cost, resultsWithBounds.cost, "The optimal solutions must be the same no matter the constraint used");
        assertTrue(resultsWithBounds.stats.numberOfNodes() <= resultsNoBounds.stats.numberOfNodes(),
                "The search should explore less nodes when using bound computation (in optimization)");
        System.out.println("  bounds: " + resultsWithBounds.stats.numberOfNodes() + " nodes\n" +
                "noBounds: " + resultsNoBounds.stats.numberOfNodes() + " nodes");


    }

    ;

    public void makeTriangularInequality(int[][] distance) {
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

    private void bellmanFord(int numNodes, int edgeCount, int[][] edges, int src, long[] dist) throws Exception {
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

    /**
     * Gives the statistics and solution cost for solving a problem optimizing a distance
     *
     * @param seqVar      sequence on which the search is applied
     * @param distance    distance to minimize
     * @param transitions transitions between nodes
     * @param r           procedure to call before starting the search (for instance a constraint to add)
     * @return statistics and solution cost when solving the problem
     */
    public StatsAndSolution searchWith(CPSeqVar seqVar, CPIntVar distance, int[][] transitions, Runnable r) {
        CPSolver cp = seqVar.getSolver();
        r.run();
        Objective travelCost = cp.minimize(distance);
        DFSearch search1 = minDetourSearch(seqVar, transitions);
        AtomicInteger bestSolNoBounds = new AtomicInteger(Integer.MAX_VALUE);
        search1.onSolution(() -> bestSolNoBounds.set(distance.min()));
        SearchStatistics statsNoBounds = search1.optimize(travelCost);
        return new StatsAndSolution(statsNoBounds, bestSolNoBounds.get());
    }

    /**
     * Search that selects the insertable node with the fewest insertions, and inserts it at its place with
     * the smallest detour cost. Ties are broken by taking the node and insertion with smallest id
     * (the search is fully deterministic)
     *
     * @param seqVar      sequence to construct
     * @param transitions transitions between nodes
     * @return search procedure for the given sequence
     */
    public DFSearch minDetourSearch(CPSeqVar seqVar, int[][] transitions) {
        return CPFactory.makeDfs(seqVar.getSolver(), minDetourSearchProcedure(seqVar, transitions).get());
    }

    public Supplier<Supplier<Runnable[]>> minDetourSearchProcedure(CPSeqVar seqVar, int[][] transitions) {
        int[] nodes = new int[seqVar.nNode()];
        CPSolver cp = seqVar.getSolver();
        return () -> () -> {
            int nInsertable = seqVar.fillNode(nodes, SeqStatus.INSERTABLE);
            if (nInsertable == 0)
                return EMPTY; // no node can be inserted -> solution found
            // selects the insertable node with the fewest remaining insertions
            int minInsert = Integer.MAX_VALUE;
            int bestNode = -1;
            for (int i = 0; i < nInsertable; i++) {
                int node = nodes[i];
                int nInserts = seqVar.nInsert(node);
                if (nInserts < minInsert || (nInserts == minInsert && node < bestNode)) { // break ties by node id
                    minInsert = nInserts;
                    bestNode = node;
                }
            }
            int node = bestNode;
            // find insertion with smallest detour cost for the node
            int nInsertions = seqVar.fillInsert(node, nodes);
            int bestDetour = Integer.MAX_VALUE;
            int bestPred = -1;
            for (int i = 0; i < nInsertions; i++) {
                int pred = nodes[i];
                int succ = seqVar.memberAfter(pred);
                int detour = transitions[pred][node] + transitions[node][succ] - transitions[pred][succ];
                if (detour < bestDetour || (detour == bestDetour && pred < bestPred)) { // break ties by predecessor id
                    bestDetour = detour;
                    bestPred = pred;
                }
            }
            // generates 2 branches: insert the node or prevent the insertion
            int pred = bestPred;
            int succ = seqVar.memberAfter(pred);
            return branch(() -> cp.post(insert(seqVar, pred, node)),
                    () -> cp.post(notBetween(seqVar, pred, node, succ)));
        };
    }

    private record CostAndSequence(int cost, int[] sequence) {
    }

    public record StatsAndSolution(SearchStatistics stats, int cost) {
    }

}
