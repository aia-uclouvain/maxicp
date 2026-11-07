/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 */

package org.maxicp.search;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.LessOrEqual;
import org.maxicp.cp.engine.constraints.Sum;
import org.maxicp.cp.engine.constraints.seqvar.Cumulative;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.Precedence;
import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.blackbox.BlackBoxSearch;
import org.maxicp.search.blackbox.SearchStatus;
import org.maxicp.search.blackbox.SequenceLNSRunnableSearch;
import org.maxicp.search.blackbox.SequenceRunnableSearch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;

public class FDSSeqVarTest {

    private static int fact(int n) {
        return n <= 1 ? 1 : n * fact(n - 1);
    }

    private static int nCk(int n, int k) {
        return fact(n) / (fact(k) * fact(n - k));
    }

    @Test
    public void testCompletenessOptionalNodes() {
        CPSolver cp = makeSolver();
        int nNodes = 6; // 4 optional nodes (0..3), start = 4, end = 5
        CPSeqVar seqVar = makeSeqVar(cp, nNodes, 4, 5);

        Set<String> solutions = new HashSet<>();
        DFSearch search = makeDfs(cp, Searches.fds(seqVar));
        search.onSolution(() -> {
            assertTrue(seqVar.isFixed());
            assertEquals(seqVar.nNode(MEMBER), seqVar.nNode(REQUIRED));
            assertTrue(solutions.add(seqVar.membersOrdered("->")));
        });

        int expected = 0;
        for (int k = 0; k <= nNodes - 2; k++) {
            expected += nCk(nNodes - 2, k) * fact(k);
        }

        SearchStatistics stats = search.solve();
        assertEquals(expected, stats.numberOfSolutions());
        assertEquals(expected, solutions.size());
    }

    @Test
    public void testCompletenessConstrainedSatisfaction() {
        // Compare exact solution set of FDSSeqVar vs firstFailBinary on a constrained sequence problem
        Set<String> expectedSolutions = solveConstrainedSeq(false);
        Set<String> fdsSolutions = solveConstrainedSeq(true);
        assertFalse(expectedSolutions.isEmpty());
        assertEquals(expectedSolutions, fdsSolutions);
    }

    private Set<String> solveConstrainedSeq(boolean useFds) {
        CPSolver cp = makeSolver();
        int nNodes = 8; // 0..2 pickups, 3..5 drops, 6 start, 7 end
        CPSeqVar seq = makeSeqVar(cp, nNodes, 6, 7);
        int[] pickups = {0, 1, 2};
        int[] drops = {3, 4, 5};
        int[] load = {2, 2, 2};
        cp.post(new Cumulative(seq, pickups, drops, load, 3));
        cp.post(new Precedence(seq, 0, 4));
        // Require nodes 0, 3, 1, 4; leave 2, 5 optional
        seq.require(0);
        seq.require(3);
        seq.require(1);
        seq.require(4);

        Set<String> sols = new HashSet<>();
        DFSearch search = makeDfs(cp, useFds ? Searches.fds(seq) : Searches.firstFailBinary(seq));
        search.onSolution(() -> {
            assertTrue(seq.isFixed());
            sols.add(seq.membersOrdered("->"));
        });
        search.solve();
        return sols;
    }

    @Test
    public void testOptimalitySmallDARPRawAndModeling() {
        // 2 vehicles, 3 requests (0..2 pickups, 3..5 drops, 6..7 starts, 8..9 ends)
        int nReq = 3;
        int nVeh = 2;
        int n = nReq * 2 + nVeh * 2;
        int[][] coords = {
                {0, 10}, {10, 0}, {20, 10}, // pickups 0, 1, 2
                {0, 25}, {25, 0}, {20, 25}, // drops 3, 4, 5
                {0, 0}, {0, 0},             // starts 6, 7
                {0, 0}, {0, 0}              // ends 8, 9
        };
        int[][] dist = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int dx = coords[i][0] - coords[j][0];
                int dy = coords[i][1] - coords[j][1];
                dist[i][j] = (int) Math.round(Math.hypot(dx, dy));
            }
        }
        int[] twStart = {5, 5, 10, 15, 15, 20, 0, 0, 0, 0};
        int[] twEnd = {40, 40, 50, 70, 70, 80, 100, 100, 100, 100};
        int[] duration = {2, 2, 2, 2, 2, 2, 0, 0, 0, 0};
        int[] pickups = {0, 1, 2};
        int[] drops = {3, 4, 5};
        int[] load = {2, 2, 2};
        int capacity = 3;

        int optRef = solveSmallDARPRaw(n, nReq, nVeh, dist, twStart, twEnd, duration, pickups, drops, load, capacity, 0);
        int optFdsGeneric = solveSmallDARPRaw(n, nReq, nVeh, dist, twStart, twEnd, duration, pickups, drops, load, capacity, 1);
        int optFdsDist = solveSmallDARPRaw(n, nReq, nVeh, dist, twStart, twEnd, duration, pickups, drops, load, capacity, 2);
        int optFdsModeling = solveSmallDARPModeling(n, nReq, nVeh, dist, twStart, twEnd, duration, pickups, drops, load, capacity);

        assertTrue(optRef < Integer.MAX_VALUE);
        assertEquals(optRef, optFdsGeneric);
        assertEquals(optRef, optFdsDist);
        assertEquals(optRef, optFdsModeling);
    }

    private int solveSmallDARPRaw(int n, int nReq, int nVeh, int[][] dist, int[] twStart, int[] twEnd,
                                  int[] duration, int[] pickups, int[] drops, int[] load, int capacity, int mode) {
        CPSolver cp = makeSolver();
        CPSeqVar[] routes = new CPSeqVar[nVeh];
        CPIntVar[] time = new CPIntVar[n];
        CPIntVar[] distance = new CPIntVar[nVeh];
        for (int i = 0; i < n; i++) {
            time[i] = makeIntVar(cp, twStart[i], twEnd[i]);
        }
        for (int v = 0; v < nVeh; v++) {
            routes[v] = makeSeqVar(cp, n, 2 * nReq + v, 2 * nReq + nVeh + v);
            distance[v] = makeIntVar(cp, 0, 200);
            cp.post(new TransitionTimes(routes[v], time, dist, duration));
            cp.post(new Cumulative(routes[v], pickups, drops, load, capacity));
            cp.post(new Distance(routes[v], dist, distance[v]));
        }
        for (int i = 0; i < nReq; i++) {
            cp.post(new LessOrEqual(time[i + nReq], plus(time[i], duration[i] + 40)));
        }
        for (int u = 0; u < n; u++) {
            CPIntVar[] visits = new CPIntVar[nVeh];
            for (int v = 0; v < nVeh; v++) {
                visits[v] = routes[v].getNodeVar(u).isRequired();
            }
            cp.post(new Sum(visits, 1));
        }
        CPIntVar sumDist = CPFactory.sum(distance);
        FDSSeqVar fds = mode == 1 ? new FDSSeqVar(routes)
                : new FDSSeqVar(0.95, 0.2, routes, (pred, node, succ) -> dist[pred][node] + dist[node][succ] - dist[pred][succ]);
        if (mode != 0) {
            fds.resetLevel();
        }
        DFSearch search = makeDfs(cp, mode == 0 ? Searches.firstFailBinary(routes) : fds);
        final int[] best = {Integer.MAX_VALUE};
        search.onSolution(() -> best[0] = sumDist.min());
        search.optimize(cp.minimize(sumDist));
        return best[0];
    }

    private int solveSmallDARPModeling(int n, int nReq, int nVeh, int[][] dist, int[] twStart, int[] twEnd,
                                       int[] duration, int[] pickups, int[] drops, int[] load, int capacity) {
        ModelDispatcher model = Factory.makeModelDispatcher();
        SeqVar[] routes = new SeqVar[nVeh];
        IntExpression[] time = new IntExpression[n];
        IntExpression[] distance = new IntExpression[nVeh];
        for (int i = 0; i < n; i++) {
            time[i] = model.intVar(twStart[i], twEnd[i]);
        }
        for (int v = 0; v < nVeh; v++) {
            routes[v] = model.seqVar(n, 2 * nReq + v, 2 * nReq + nVeh + v);
            distance[v] = model.intVar(0, 200);
            model.add(Factory.transitionTimes(routes[v], time, dist, duration));
            model.add(Factory.cumulative(routes[v], pickups, drops, load, capacity));
            model.add(Factory.distance(routes[v], dist, distance[v]));
        }
        for (int i = 0; i < nReq; i++) {
            model.add(Factory.le(time[i + nReq], Factory.plus(time[i], duration[i] + 40)));
        }
        for (int u = 0; u < n; u++) {
            final int node = u;
            IntExpression[] visits = model.intVarArray(nVeh, v -> routes[v].isNodeRequired(node));
            model.add(Factory.eq(Factory.sum(visits), 1));
        }
        IntExpression sumDist = Factory.sum(distance);
        ConcreteCPModel cp = model.cpInstantiate();
        DFSearch search = cp.dfSearch(Searches.fds(routes, dist));
        final int[] best = {Integer.MAX_VALUE};
        search.onSolution(() -> best[0] = sumDist.min());
        search.optimize(cp.minimize(sumDist));
        return best[0];
    }

    @Test
    public void testPrimalSeqVarCompletenessAndOptimality() {
        CPSolver cp = makeSolver();
        int nNodes = 6; // 4 optional nodes (0..3), start = 4, end = 5
        CPSeqVar seqVar = makeSeqVar(cp, nNodes, 4, 5);

        Set<String> solutions = new HashSet<>();
        DFSearch search = makeDfs(cp, Searches.primalSeqVar(seqVar));
        search.onSolution(() -> {
            assertTrue(seqVar.isFixed());
            assertEquals(seqVar.nNode(MEMBER), seqVar.nNode(REQUIRED));
            assertTrue(solutions.add(seqVar.membersOrdered("->")));
        });

        int expected = 0;
        for (int k = 0; k <= nNodes - 2; k++) {
            expected += nCk(nNodes - 2, k) * fact(k);
        }

        SearchStatistics stats = search.solve();
        assertEquals(expected, stats.numberOfSolutions());
        assertEquals(expected, solutions.size());
    }

    @Test
    public void testBlackBoxSearchSeqVarModelingAutoAndExplicit() {
        int nReq = 3;
        int nVeh = 2;
        int n = nReq * 2 + nVeh * 2;
        int[][] coords = {
                {0, 10}, {10, 0}, {20, 10},
                {0, 25}, {25, 0}, {20, 25},
                {0, 0}, {0, 0},
                {0, 0}, {0, 0}
        };
        int[][] dist = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int dx = coords[i][0] - coords[j][0];
                int dy = coords[i][1] - coords[j][1];
                dist[i][j] = (int) Math.round(Math.hypot(dx, dy));
            }
        }
        int[] twStart = {5, 5, 10, 15, 15, 20, 0, 0, 0, 0};
        int[] twEnd = {40, 40, 50, 70, 70, 80, 100, 100, 100, 100};
        int[] duration = {2, 2, 2, 2, 2, 2, 0, 0, 0, 0};
        int[] pickups = {0, 1, 2};
        int[] drops = {3, 4, 5};
        int[] load = {2, 2, 2};
        int capacity = 3;

        int optRef = solveSmallDARPRaw(n, nReq, nVeh, dist, twStart, twEnd, duration, pickups, drops, load, capacity, 0);

        // 1. Auto-discovered SeqVars via model.blackBoxSearch()
        ModelDispatcher model = Factory.makeModelDispatcher();
        SeqVar[] routes = new SeqVar[nVeh];
        IntExpression[] time = new IntExpression[n];
        IntExpression[] distance = new IntExpression[nVeh];
        for (int i = 0; i < n; i++) {
            time[i] = model.intVar(twStart[i], twEnd[i]);
        }
        for (int v = 0; v < nVeh; v++) {
            routes[v] = model.seqVar(n, 2 * nReq + v, 2 * nReq + nVeh + v);
            distance[v] = model.intVar(0, 200);
            model.add(Factory.transitionTimes(routes[v], time, dist, duration));
            model.add(Factory.cumulative(routes[v], pickups, drops, load, capacity));
            model.add(Factory.distance(routes[v], dist, distance[v]));
        }
        for (int i = 0; i < nReq; i++) {
            model.add(Factory.le(time[i + nReq], Factory.plus(time[i], duration[i] + 40)));
        }
        for (int u = 0; u < n; u++) {
            final int node = u;
            IntExpression[] visits = model.intVarArray(nVeh, v -> routes[v].isNodeRequired(node));
            model.add(Factory.eq(Factory.sum(visits), 1));
        }
        IntExpression sumDist = Factory.sum(distance);

        BlackBoxSearch bbAuto = model.blackBoxSearch();
        SearchStatistics statsOpt = bbAuto.optimize(model.minimize(sumDist));
        assertTrue(statsOpt.numberOfSolutions() >= 1);
        assertTrue(bbAuto.bestObjectiveValue().isPresent());
        assertEquals(optRef, bbAuto.bestObjectiveValue().get());
        assertEquals(SearchStatus.PROVEN_OPTIMAL, bbAuto.status());
        assertTrue(bbAuto.bestSeqSolution().isPresent());
        assertEquals(nVeh, bbAuto.bestSeqSolution().get().size());

        // 2. Explicit SeqVars via model.blackBoxSearch(routes) on feasibility
        BlackBoxSearch bbExplicit = model.blackBoxSearch(routes);
        SearchStatistics statsSat = bbExplicit.solve();
        assertEquals(1, statsSat.numberOfSolutions());
        assertEquals(SearchStatus.SAT, bbExplicit.status());
        assertTrue(bbExplicit.bestSeqSolution().isPresent());
    }

    @Test
    public void testBlackBoxSearchSeqVarRawAutoAndExplicit() {
        int nReq = 3;
        int nVeh = 2;
        int n = nReq * 2 + nVeh * 2;
        int[][] coords = {
                {0, 10}, {10, 0}, {20, 10},
                {0, 25}, {25, 0}, {20, 25},
                {0, 0}, {0, 0},
                {0, 0}, {0, 0}
        };
        int[][] dist = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int dx = coords[i][0] - coords[j][0];
                int dy = coords[i][1] - coords[j][1];
                dist[i][j] = (int) Math.round(Math.hypot(dx, dy));
            }
        }
        int[] twStart = {5, 5, 10, 15, 15, 20, 0, 0, 0, 0};
        int[] twEnd = {40, 40, 50, 70, 70, 80, 100, 100, 100, 100};
        int[] duration = {2, 2, 2, 2, 2, 2, 0, 0, 0, 0};
        int[] pickups = {0, 1, 2};
        int[] drops = {3, 4, 5};
        int[] load = {2, 2, 2};
        int capacity = 3;

        int optRef = solveSmallDARPRaw(n, nReq, nVeh, dist, twStart, twEnd, duration, pickups, drops, load, capacity, 0);

        CPSolver cp = makeSolver();
        CPSeqVar[] routes = new CPSeqVar[nVeh];
        CPIntVar[] time = new CPIntVar[n];
        CPIntVar[] distance = new CPIntVar[nVeh];
        for (int i = 0; i < n; i++) {
            time[i] = makeIntVar(cp, twStart[i], twEnd[i]);
        }
        for (int v = 0; v < nVeh; v++) {
            routes[v] = makeSeqVar(cp, n, 2 * nReq + v, 2 * nReq + nVeh + v);
            distance[v] = makeIntVar(cp, 0, 200);
            cp.post(new TransitionTimes(routes[v], time, dist, duration));
            cp.post(new Cumulative(routes[v], pickups, drops, load, capacity));
            cp.post(new Distance(routes[v], dist, distance[v]));
        }
        for (int i = 0; i < nReq; i++) {
            cp.post(new LessOrEqual(time[i + nReq], plus(time[i], duration[i] + 40)));
        }
        for (int u = 0; u < n; u++) {
            CPIntVar[] visits = new CPIntVar[nVeh];
            for (int v = 0; v < nVeh; v++) {
                visits[v] = routes[v].getNodeVar(u).isRequired();
            }
            cp.post(new Sum(visits, 1));
        }
        CPIntVar sumDist = CPFactory.sum(distance);

        BlackBoxSearch bbRaw = cp.blackBoxSearch();
        SearchStatistics statsRaw = bbRaw.optimize(cp.minimize(sumDist));
        assertTrue(statsRaw.numberOfSolutions() >= 1);
        assertTrue(bbRaw.bestObjectiveValue().isPresent());
        assertEquals(optRef, bbRaw.bestObjectiveValue().get());
        assertEquals(SearchStatus.PROVEN_OPTIMAL, bbRaw.status());

        BlackBoxSearch bbRawExplicit = CPFactory.makeBlackBox(cp, routes);
        SearchStatistics statsSat = bbRawExplicit.solve();
        assertEquals(1, statsSat.numberOfSolutions());
        assertEquals(SearchStatus.SAT, bbRawExplicit.status());
    }

    @Test
    public void testSequenceLNSPhaseExecution() {
        int nReq = 4;
        int nVeh = 2;
        int n = nReq * 2 + nVeh * 2;
        int[][] coords = {
                {0, 10}, {10, 0}, {20, 10}, {15, 20},
                {0, 25}, {25, 0}, {20, 25}, {30, 25},
                {0, 0}, {0, 0},
                {0, 0}, {0, 0}
        };
        int[][] dist = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int dx = coords[i][0] - coords[j][0];
                int dy = coords[i][1] - coords[j][1];
                dist[i][j] = (int) Math.round(Math.hypot(dx, dy));
            }
        }
        int[] twStart = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        int[] twEnd = {200, 200, 200, 200, 200, 200, 200, 200, 200, 200, 200, 200};
        int[] duration = {2, 2, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0};
        int[] pickups = {0, 1, 2, 3};
        int[] drops = {4, 5, 6, 7};
        int[] load = {1, 1, 1, 1};
        int capacity = 3;

        ModelDispatcher model = Factory.makeModelDispatcher();
        SeqVar[] routes = new SeqVar[nVeh];
        IntExpression[] time = new IntExpression[n];
        IntExpression[] distance = new IntExpression[nVeh];
        for (int i = 0; i < n; i++) {
            time[i] = model.intVar(twStart[i], twEnd[i]);
        }
        for (int v = 0; v < nVeh; v++) {
            routes[v] = model.seqVar(n, 2 * nReq + v, 2 * nReq + nVeh + v);
            distance[v] = model.intVar(0, 300);
            model.add(Factory.transitionTimes(routes[v], time, dist, duration));
            model.add(Factory.cumulative(routes[v], pickups, drops, load, capacity));
            model.add(Factory.distance(routes[v], dist, distance[v]));
        }
        for (int u = 0; u < n; u++) {
            final int node = u;
            IntExpression[] visits = model.intVarArray(nVeh, v -> routes[v].isNodeRequired(node));
            model.add(Factory.eq(Factory.sum(visits), 1));
        }
        IntExpression sumDist = Factory.sum(distance);
        Objective obj = model.minimize(sumDist);

        BlackBoxSearch bb = new BlackBoxSearch(model, obj);
        SequenceRunnableSearch initPhase = new SequenceRunnableSearch(bb, model, List.of(routes), List.of(sumDist), null, 42L);
        SequenceLNSRunnableSearch lnsPhase = new SequenceLNSRunnableSearch(bb, model, List.of(routes), List.of(sumDist), obj, 30, 50, 43L);
        bb.addPhase("init", initPhase, 0.25, false);
        bb.addPhase("lns", lnsPhase, 0.75, true);

        model.runCP(() -> {
            SearchStatus s1 = initPhase.run(100L);
            assertEquals(SearchStatus.SAT, s1);
            assertTrue(bb.bestSeqSolution().isPresent());
            assertTrue(bb.bestObjectiveValue().isPresent());
            int firstObj = bb.bestObjectiveValue().get();
            SearchStatus s2 = lnsPhase.run(120L);
            assertTrue(s2 == SearchStatus.IMPROVED || s2 == SearchStatus.NOT_IMPROVED);
            assertTrue(bb.bestObjectiveValue().get() <= firstObj);
        });
    }

    @Test
    public void testInvalidArguments() {
        CPSolver cp = makeSolver();
        CPSeqVar seq = makeSeqVar(cp, 4, 2, 3);
        assertThrows(IllegalArgumentException.class, () -> new FDSSeqVar(new SeqVar[0]));
        assertThrows(IllegalArgumentException.class, () -> new FDSSeqVar(0.0, seq));
        assertThrows(IllegalArgumentException.class, () -> new FDSSeqVar(1.0, seq));
        assertThrows(IllegalArgumentException.class, () -> new PrimalSeqVar(new SeqVar[0]));
    }
}

