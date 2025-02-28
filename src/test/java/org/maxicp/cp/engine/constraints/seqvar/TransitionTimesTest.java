/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.seqvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.*;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.state.StateManager;
import org.maxicp.util.exception.InconsistencyException;

import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.EMPTY;

public class TransitionTimesTest extends CPSolverTest {
    
    static int nNodes = 6;
    static int start = 4;
    static int end = 5;
    static int[][] transitions = new int[][] {
            {0, 3, 5, 4, 4, 4},
            {3, 0, 4, 5, 5, 5},
            {5, 4, 0, 3, 9, 9},
            {4, 5, 3, 0, 8, 8},
            {4, 5, 9, 8, 0, 0},
            {4, 5, 9, 8, 0, 0},
    };
    static int[] serviceTime = new int[] {5, 5, 5, 5, 0, 0};

    public static Stream<CPSeqVar> getSeqVar() {
        return getSolver().map(cp -> CPFactory.makeSeqVar(cp, nNodes, start, end));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testOneNodeReachable(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        StateManager sm = cp.getStateManager();
        CPIntVar[] time = new CPIntVar[] {
                CPFactory.makeIntVar(cp, 0, 10),
                CPFactory.makeIntVar(cp, 0, 0),
                CPFactory.makeIntVar(cp, 0, 0),
                CPFactory.makeIntVar(cp, 0, 0),
                CPFactory.makeIntVar(cp, 0, 0),
                CPFactory.makeIntVar(cp, 100, 200),
        };
        // only the node 0 is reachable with those time available
        cp.post(new TransitionTimes(seqVar, time, transitions, serviceTime));

        int[] member1 = new int[] {start, end};
        int[] possible1= new int[] {0};
        int[] excluded1 = new int[] {1, 2, 3};

        CPSeqVarAssertion.assertSeqVar(seqVar, member1, possible1, excluded1);
        sm.saveState();
        seqVar.insert(seqVar.start(), 0);
        cp.fixPoint();
        // seqVar at this point: start -> 0 -> end

        int[] member2 = new int[] {start, 0, end};
        int[] possible2= new int[] {};
        assertEquals(4, time[0].min());
        assertEquals(10, time[0].max());
        assertEquals(100, time[5].min()); // end node is not affected by the changes in this case
        assertEquals(200, time[5].max());
        CPSeqVarAssertion.assertSeqVar(seqVar, member2, possible2, excluded1);
        sm.restoreState();
        CPSeqVarAssertion.assertSeqVar(seqVar, member1, possible1, excluded1);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testOneNodeUnreachable(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        StateManager sm = cp.getStateManager();
        CPIntVar[] time = new CPIntVar[] {
                CPFactory.makeIntVar(cp, 0, 20),
                CPFactory.makeIntVar(cp, 12, 16),
                CPFactory.makeIntVar(cp, 0, 20),
                CPFactory.makeIntVar(cp, 4, 7), // node 3 unreachable from the seqVar (distance from start: 8)
                CPFactory.makeIntVar(cp, 0, 20),
                CPFactory.makeIntVar(cp, 100, 200),
        };
        cp.post(new TransitionTimes(seqVar, time, transitions, serviceTime));

        int[] member1 = new int[] {start, end};
        int[] possible1= new int[] {0, 1, 2};
        int[] excluded1 = new int[] {3};

        CPSeqVarAssertion.assertSeqVar(seqVar, member1, possible1, excluded1);

        sm.saveState();
        seqVar.insert(seqVar.start(), 0);
        seqVar.insert(0, 2); // sequence: start -> 0 -> 2 -> end
        cp.fixPoint();
        // node 1 is now unreachable
        int[] member2 = new int[] {start, 0, 2, end};
        int[] possible2 = new int[] {};
        int[] excluded2 = new int[] {1, 3};

        /* seqVar at this point: start -> 0 -> 2 -> 5
        initial time windows:
        start:  0..20
        0:      0..20
        2:      0..20
        end:    100..200
         */

        // check for updates in time windows
        assertEquals(0, time[start].min()); // min departure time remains unchanged
        assertEquals(4, time[0].min());
        assertEquals(14, time[2].min());
        assertEquals(100, time[end].min()); // end node is not affected by the changes in this case

        assertEquals(200, time[end].max());
        assertEquals(20, time[2].max());
        assertEquals(10, time[0].max()); // reduced as node 2 must be reachable from here
        assertEquals(6, time[start].max()); // max departure time must allow reaching node 0

        // excluded nodes should not have their time window updated
        assertEquals(12, time[1].min());
        assertEquals(16, time[1].max());
        assertEquals(4, time[3].min());
        assertEquals(7, time[3].max());

        CPSeqVarAssertion.assertSeqVar(seqVar, member2, possible2, excluded2);
        sm.restoreState();
        CPSeqVarAssertion.assertSeqVar(seqVar, member1, possible1, excluded1);
    }

    // assign a route that would violate the transition time
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testUnfeasibleTransitions(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        CPIntVar[] time = new CPIntVar[] {
                CPFactory.makeIntVar(cp, 0, 20),
                CPFactory.makeIntVar(cp, 12, 16),
                CPFactory.makeIntVar(cp, 0, 20),
                CPFactory.makeIntVar(cp, 4, 7), // node 3 unreachable from the seqVar (distance from start: 8)
                CPFactory.makeIntVar(cp, 0, 20),
                CPFactory.makeIntVar(cp, 100, 200),
        };
        seqVar.insert(start, 3);
        assertThrowsExactly(InconsistencyException.class,
                () -> cp.post(new TransitionTimes(seqVar, time, transitions, serviceTime)));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testUpdateTWRequiredNode(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        CPIntVar[] time = new CPIntVar[] {
                CPFactory.makeIntVar(cp, 0, 200), // node set as required
                CPFactory.makeIntVar(cp, 12, 87),
                CPFactory.makeIntVar(cp, 5, 30),
                CPFactory.makeIntVar(cp, 4, 28),
                CPFactory.makeIntVar(cp, 0, 20), // start
                CPFactory.makeIntVar(cp, 0, 100), // end
        };
        cp.post(new TransitionTimes(seqVar, time, transitions, serviceTime));
        cp.getStateManager().saveState();
        seqVar.insert(start, 2); // start -> 2 -> end
        seqVar.require(0);
        cp.fixPoint();
        cp.getStateManager().saveState();
        seqVar.insert(start, 0);
        cp.fixPoint();
        int earliest = time[0].min();
        cp.getStateManager().restoreState();
        seqVar.insert(2, 0);
        cp.fixPoint();
        int latest = time[0].max();
        cp.getStateManager().restoreState();
        seqVar.insert(start, 2); // start -> 2 -> end
        seqVar.require(0);
        cp.fixPoint();
        assertEquals(earliest, time[0].min());
        assertEquals(latest, time[0].max());
        seqVar.insert(start, 1); // start -> 1 -> 2 -> end
        seqVar.removeDetour(start, 0, seqVar.memberAfter(start));
        // 0 cannot be inserted after start
        cp.fixPoint();
        earliest = Integer.MAX_VALUE;
        for (int i: new int[] {1, 2, 3}) {
            earliest = Math.min(earliest, time[i].min() + serviceTime[i] + transitions[i][0]);
        }
        latest = Integer.MIN_VALUE;
        for (int i: new int[] {1, 2, 3, end}) {
            latest = Math.max(latest, time[i].max() - serviceTime[0] - transitions[0][i]);
        }
        assertTrue(time[0].min() >= earliest);
        assertTrue(time[0].max() <= latest);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFindAllSolutions1(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        // tries to find all solutions for a route where every node can be visited
        CPIntVar[] time = new CPIntVar[] {
                CPFactory.makeIntVar(cp, 0, 200),
                CPFactory.makeIntVar(cp, 0, 200),
                CPFactory.makeIntVar(cp, 0, 200),
                CPFactory.makeIntVar(cp, 0, 200),
                CPFactory.makeIntVar(cp, 0, 200),
                CPFactory.makeIntVar(cp, 0, 200),
        };
        cp.post(new TransitionTimes(seqVar, time, transitions, serviceTime));
        DFSearch dfs = CPFactory.makeDfs(cp, insertEverywhere(seqVar));
        dfs.onSolution(() -> assertFixedseqVar(seqVar, time, transitions, serviceTime));
        SearchStatistics statistics = dfs.solve();
        assertEquals(24, statistics.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFindAllSolutions2(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        // tries to find all solutions for a route where not all nodes can be visited
        int nNodes = 9;
        CPSeqVar seqvar = CPFactory.makeSeqVar(cp, nNodes, nNodes-2, nNodes-1);
        int[][] dist = new int[nNodes][nNodes];
        for (int i = 0 ; i < nNodes ; ++i) {
            for (int j = i+1 ; j < nNodes ; ++j) {
                dist[i][j] = 10;
                dist[j][i] = 10;
            }
        }
        int[] serviceTime = IntStream.range(0, nNodes).map(i -> 5).toArray();
        CPIntVar totalDist = CPFactory.makeIntVar(cp, 0, 100);
        CPIntVar[] time = IntStream.range(0, nNodes)
                .mapToObj(i -> CPFactory.makeIntVar(cp, 0, 110))
                .toArray(CPIntVar[]::new);
        cp.post(new TransitionTimes(seqvar, time, dist, serviceTime));
        cp.post(new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                propagate();
                seqvar.propagateOnExclude(this);
            }

            @Override
            public void propagate() {
                if (seqvar.nNode(EXCLUDED) > 1) {
                    throw InconsistencyException.INCONSISTENCY;
                }
            }
        });
        DFSearch dfs = CPFactory.makeDfs(cp, insertEverywhereOrExclude(seqvar));
        dfs.onSolution(() -> assertFixedseqVar(seqvar, time, dist, serviceTime));
        SearchStatistics statistics = dfs.solve();
        // 6 nodes can be visited in addition to start and end + 1 node is excluded
        assertEquals(fact(6) * (nNodes-2), statistics.numberOfSolutions());
    }

        @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFindAllSolutions3(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        // tries to find all solutions for a route where not all can be visited because of maximum distance exceeded
        CPIntVar[] time = new CPIntVar[] {
                CPFactory.makeIntVar(cp, 0, 200),
                CPFactory.makeIntVar(cp, 0, 200),
                CPFactory.makeIntVar(cp, 0, 200),
                CPFactory.makeIntVar(cp, 0, 200),
                CPFactory.makeIntVar(cp, 0, 200),
                CPFactory.makeIntVar(cp, 0, 200),
        };
        int[][] dist = new int[6][6];
        for (int i = 0 ; i < 6 ; ++i) {
            for (int j = i+1 ; j < 6 ; ++j) {
                dist[i][j] = 10;
                dist[j][i] = 10;
            }
        }
        CPIntVar totalDist = CPFactory.makeIntVar(cp, 0, 35);
        cp.post(new Distance(seqVar, totalDist, dist));
        cp.post(new TransitionTimes(seqVar, time, dist));
        cp.post(new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                propagate();
                seqVar.propagateOnExclude(this);
            }

            @Override
            public void propagate() {
                if (seqVar.nNode(EXCLUDED) > 2) {
                    throw InconsistencyException.INCONSISTENCY;
                }
            }
        });
        DFSearch dfs = CPFactory.makeDfs(cp, insertEverywhereOrExclude(seqVar));
        dfs.onSolution(() -> assertFixedseqVar(seqVar, time, dist, new int[6]));
        SearchStatistics statistics = dfs.solve();
        assertEquals(12, statistics.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testUpdateFromTW(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        CPIntVar[] time = new CPIntVar[] {
                CPFactory.makeIntVar(cp, 0, 200),
                CPFactory.makeIntVar(cp, 12, 16),
                CPFactory.makeIntVar(cp, 20, 30),
                CPFactory.makeIntVar(cp, 4, 28),
                CPFactory.makeIntVar(cp, 0, 20), // start
                CPFactory.makeIntVar(cp, 0, 100), // end
        };
        cp.post(new TransitionTimes(seqVar, time, transitions, serviceTime));
        seqVar.insert(start, 3); // start -> 3 -> end
        cp.fixPoint();
        assertTrue(seqVar.hasInsert(start, 1));
        time[3].fix(time[3].min());
        cp.fixPoint();
        assertEquals(1, seqVar.nPred(3));
        for (int node: new int[] {0, 1, 2}) {
            assertFalse(seqVar.hasInsert(start, node));
        }
    }

    private static int fact(int i) {
        if (i <= 1)
            return 1;
        return i * fact(i-1);
    }

    private static void assertFixedseqVar(CPSeqVar seqVar, CPIntVar[] time, int[][] dist, int[] serviceTime) {
        assertTrue(seqVar.isFixed());
        int[] members = new int[seqVar.nNode(MEMBER_ORDERED)];
        seqVar.fillNode(members, MEMBER_ORDERED);
        // first fast test
        for (int i = 0 ; i < members.length - 1 ; ++i) {
            assertTrue(time[members[i]].min() <= time[members[i+1]].min());
            assertTrue(time[members[i+1]].max() >= time[members[i]].max());
        }
        // ensure that the time windows are indeed valids
        int pred = members[0];
        int timePred = time[pred].min();
        int node = members[1];
        for (int i = 2 ; i <= members.length; ++i) {
            int arrival = timePred + serviceTime[pred] + dist[pred][node];
            assertTrue(arrival <= time[node].min());
            timePred = Math.max(arrival, time[node].min());
            pred = node;
            node = i < members.length ? members[i] : 0;
        }
    }

    private static Supplier<Runnable[]> insertEverywhere(CPSeqVar seqVar) {
        int[] nodes = new int[seqVar.nNode()];
        int[] preds = new int[seqVar.nNode()];
        return () -> {
            int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
            if (nInsertable == 0) {
                return EMPTY;
            } else {
                int branchingNode = nodes[0];
                int nPred = seqVar.fillInsert(branchingNode, preds);
                assertTrue(nPred > 0);
                Runnable[] branching = new Runnable[nPred];
                for (int i = 0 ; i < nPred ; ++i) {
                    int pred = preds[i];
                    branching[i] = () -> {
                        seqVar.getSolver().post(new Insert(seqVar, pred, branchingNode));
                    };
                }
                return branching;
            }
        };
    }

    private static Supplier<Runnable[]> insertEverywhereOrExclude(CPSeqVar seqVar) {
        int[] nodes = new int[seqVar.nNode()];
        int[] preds = new int[seqVar.nNode()];
        return () -> {
            int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
            if (nInsertable == 0) {
                return EMPTY;
            } else {
                int branchingNode = nodes[0];
                int nPred = seqVar.fillInsert(branchingNode, preds);
                Runnable[] branching = new Runnable[nPred+1];
                for (int i = 0 ; i < nPred ; ++i) {
                    int pred = preds[i];
                    branching[i] = () -> {
                        seqVar.getSolver().post(new Insert(seqVar, pred, branchingNode));
                    };
                }
                branching[nPred] = () -> {
                    seqVar.getSolver().post(new Exclude(seqVar, branchingNode));
                };
                return branching;
            }
        };
    }

}