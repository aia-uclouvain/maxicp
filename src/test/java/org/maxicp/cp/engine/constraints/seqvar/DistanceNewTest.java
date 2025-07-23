package org.maxicp.cp.engine.constraints.seqvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

public class DistanceNewTest extends CPSolverTest {

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
     * Ensures that all solutions to a small instance may be retrieved
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFindAllSolutions1(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        CPIntVar dist = CPFactory.makeIntVar(cp, 0, 100);
        cp.post(new DistanceNew(seqVar, transitions, dist));
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
     * @param nNodes number of nodes in the sequence
     * @param seed seed used for random number generation
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
        CPSeqVar seqVar = CPFactory.makeSeqVar(cp, nNodes, nNodes-2, nNodes-1);
        for (int node = 0 ; node < nNodes; node++)
            seqVar.require(node);
        // rough upper bound on the maximum travel distance
        int roughUpperBound = Arrays.stream(transitions).mapToInt(arr -> Arrays.stream(arr).max().getAsInt()).sum();
        CPIntVar distance = CPFactory.makeIntVar(cp, 0, roughUpperBound);
        cp.post(new Distance(seqVar, transitions, distance));

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

    /**
     * Ensures that some trivial inconsistent cases, where the sequence cannot match the distance, are detected
     */
    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDetectInfeasibility(CPSolver cp) {
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
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new DistanceNew(seqVar, transitions, dist)));
    }

    /**
     * Test on small TSP instances that the search using bounds explore less search nodes than without using bounds
     */
    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
        nNodes, seed
        25,     1
        25,     2
        25,     42
        """)
    public void testLessSearchNodesUsingBound(int nNodes, int seed) {
        // instance data
        Random random = new Random(seed);
        int[][] transitions = randomTransitions(random, nNodes);
        int roughUpperBound = Arrays.stream(transitions).mapToInt(arr -> Arrays.stream(arr).max().getAsInt()).sum();
        // model
        CPSolver cp = makeSolver();
        CPSeqVar seqVar = CPFactory.makeSeqVar(cp, nNodes, nNodes-2, nNodes-1);
        for (int node = 0 ; node < nNodes; node++)
            seqVar.require(node);
        CPIntVar distance = CPFactory.makeIntVar(cp, 0, roughUpperBound);

        // search without using bound computation
        cp.getStateManager().saveState();
        StatsAndSolution resultsNoBounds = searchWith(seqVar, distance, transitions,
                () -> cp.post(new Distance(seqVar, transitions, distance)));
        cp.getStateManager().restoreState();

        // search using bound computation
        StatsAndSolution resultsWithBounds = searchWith(seqVar, distance, transitions,
                () -> cp.post(new DistanceNew(seqVar, transitions, distance)));

        // compare the 2 searches
        assertEquals(resultsNoBounds.cost, resultsWithBounds.cost, "The optimal solutions must be the same no matter the constraint used");
        assertTrue(resultsWithBounds.stats.numberOfNodes() < resultsNoBounds.stats.numberOfNodes(),
                "The search should explore strictly less nodes when using bound computation (in optimization)");
        System.out.println("  bounds: " + resultsWithBounds.stats.numberOfNodes() + " nodes\n" +
                "noBounds: " + resultsNoBounds.stats.numberOfNodes() + " nodes");
    }

    /**
     * Gives the statistics and solution cost for solving a problem optimizing a distance
     * @param seqVar sequence on which the search is applied
     * @param distance distance to minimize
     * @param transitions transitions between nodes
     * @param r procedure to call before starting the search (for instance a constraint to add)
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

    public record StatsAndSolution(SearchStatistics stats, int cost) {};

    /**
     * Search that selects the insertable node with the fewest insertions, and inserts it at its place with
     * the smallest detour cost. Ties are broken by taking the node and insertion with smallest id
     * (the search is fully deterministic)
     * @param seqVar sequence to construct
     * @param transitions transitions between nodes
     * @return search procedure for the given sequence
     */
    public DFSearch minDetourSearch(CPSeqVar seqVar, int[][] transitions) {
        int[] nodes = new int[seqVar.nNode()];
        CPSolver cp = seqVar.getSolver();
        return CPFactory.makeDfs(cp, () -> {
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
            int succ =  seqVar.memberAfter(pred);
            return branch(() -> cp.post(insert(seqVar, pred, node)),
                    () -> cp.post(notBetween(seqVar, pred, node, succ)));
        });
    }

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
     * @param random rng used to generate the matrix
     * @param nNodes number of nodes to include
     * @return random distance matrix
     */
    public static int[][] randomTransitions(Random random, int nNodes) {
        int[][] positions = new int[nNodes][];
        for (int i = 0; i < nNodes; i++) {
            int x = random.nextInt(100);
            int y = random.nextInt(100);
            positions[i] = new int[] {x, y};
        }
        return positionToDistances(positions);
    }

    /**
     * Transform a list of coordinates into a matrix of Euclidean distances (rounded up)
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

}
