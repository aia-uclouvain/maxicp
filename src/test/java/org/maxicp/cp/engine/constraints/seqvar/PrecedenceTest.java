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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.*;

public class PrecedenceTest extends CPSolverTest {

    static int nNodes = 10;
    static int start = 8;
    static int end = 9;

    public static Stream<CPSeqVar> getSeqVar() {
        return getSolver().map(cp -> CPFactory.makeSeqVar(cp, nNodes, start, end));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testInitPrecedence1(CPSeqVar seqVar) {
        assertDoesNotThrow(() -> seqVar.getSolver().post(new Precedence(seqVar, 0, 1, 2)));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testInitPrecedence2(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        cp.post(new Precedence(seqVar, 0, 2, 4, 7));
        int[] member = new int[]{start, end};
        int[] possible = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
        int[] excluded = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member, possible, excluded);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testForceInsertions1(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        cp.post(new Precedence(seqVar, 0, 2, 4, 7));
        cp.post(new Require(seqVar, 0)); // start -> 0 -> end
        cp.post(new Require(seqVar, 2)); // start -> 0 -> 2 -> end
        cp.post(new Require(seqVar, 4)); // start -> 0 -> 2 -> 4 -> end
        cp.post(new Require(seqVar, 7)); // start -> 0 -> 2 -> 4 -> 7 -> end
        int[] member = new int[]{start, 0, 2, 4, 7, end};
        int[] possible = new int[]{1, 3, 5, 6};
        int[] excluded = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member, possible, excluded);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testForceInsertions2(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        cp.post(new Precedence(seqVar, 0, 2, 4, 7));
        cp.post(new Require(seqVar, 7)); // start -> 7 -> end
        cp.post(new Require(seqVar, 4)); // start -> 4 -> 7 -> end
        cp.post(new Require(seqVar, 2)); // start -> 2 -> 4 -> 7 -> end
        cp.post(new Require(seqVar, 0)); // start -> 0 -> 2 -> 4 -> 7 -> end
        int[] member = new int[]{start, 0, 2, 4, 7, end};
        int[] possible = new int[]{1, 3, 5, 6};
        int[] excluded = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member, possible, excluded);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFilterInsert1(CPSeqVar seqVar) {
        seqVar.insert(start, 0);
        seqVar.insert(0, 1); // start -> 0 -> 1 -> end
        seqVar.removeDetour(start, 2, 0);
        seqVar.require(2);
        seqVar.require(3);
        CPSolver cp = seqVar.getSolver();

        cp.post(new Precedence(seqVar, 2, 3));
        // no node should be inserted when posting this precedence
        assertEquals(4, seqVar.nNode(MEMBER));

        // node 2 cannot be inserted between start and 0
        // hence node 3, which must come after node 2, cannot be inserted there as well
        for (int node: new int[] {2, 3}) {
            assertFalse(seqVar.hasInsert(start, node));
            assertTrue(seqVar.hasInsert(0, node));
            assertTrue(seqVar.hasInsert(1, node));
        }

        cp.getStateManager().saveState();
        cp.post(new Insert(seqVar, 0, 2)); // start -> 0 -> 2 -> 1 -> end
        assertFalse(seqVar.isNode(3, MEMBER));
        cp.getStateManager().restoreState();

        cp.post(new Insert(seqVar, 1, 2)); // start -> 0 -> 1 -> 2 -> end
        assertTrue(seqVar.isNode(3, MEMBER));
        int[] expected = new int[] {start, 0, 1, 2, 3, end};
        int[] actual = new int[seqVar.nNode(MEMBER)];
        seqVar.fillNode(actual, MEMBER_ORDERED);
        assertArrayEquals(expected, actual);

    }


    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFilterInsert2(CPSeqVar seqVar) {
        seqVar.insert(start, 0);
        seqVar.insert(0, 1); // start -> 0 -> 1 -> end
        seqVar.removeDetour(1, 3, end);
        seqVar.require(2);
        seqVar.require(3);
        CPSolver cp = seqVar.getSolver();

        cp.post(new Precedence(seqVar, 2, 3));
        // no node should be inserted when posting this precedence
        assertEquals(4, seqVar.nNode(MEMBER));

        // node 3 cannot be inserted between 1 and 0
        // hence node 2, which must come before node 3, cannot be inserted there as well
        for (int node: new int[] {2, 3}) {
            assertTrue(seqVar.hasInsert(start, node));
            assertTrue(seqVar.hasInsert(0, node));
            assertFalse(seqVar.hasInsert(1, node));
        }

        cp.getStateManager().saveState();
        cp.post(new Insert(seqVar, 0, 3)); // start -> 0 -> 3 -> 1 -> end
        assertFalse(seqVar.isNode(2, MEMBER));
        cp.getStateManager().restoreState();


        cp.post(new Insert(seqVar, start, 3)); // start -> 3 -> 0 -> 1 -> end
        assertTrue(seqVar.isNode(2, MEMBER));
        int[] expected = new int[] {start, 2, 3, 0, 1, end};
        int[] actual = new int[seqVar.nNode(MEMBER)];
        seqVar.fillNode(actual, MEMBER_ORDERED);
        assertArrayEquals(expected, actual);

    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFilterInsert3(CPSeqVar seqVar) {
        seqVar.insert(start, 0);
        seqVar.insert(0, 1);
        seqVar.insert(1, 2);
        // start -> 0 -> 1 -> 2 -> end
        seqVar.removeDetour(start, 3, 1);
        seqVar.removeDetour(1, 4, end);
        seqVar.require(3);
        seqVar.require(4);
        CPSolver cp = seqVar.getSolver();
        // node 3 can only be inserted between 1-2 or between 2-end
        // node 4 can only be inserted between start-0 or between 0-1
        // therefore, there is no way to get the precedence 4 -> 5 to hold
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new Precedence(seqVar, 3, 4)));

    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testForceInsertions3(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        cp.post(new Precedence(seqVar, 0, 2, 4, 7));
        cp.post(new Require(seqVar, 7)); // start -> 7 -> end
        cp.post(new Require(seqVar, 2)); // start -> 2 -> 7 -> end
        cp.post(new Require(seqVar, 0)); // start -> 0 -> 2 -> 7 -> end
        cp.post(new Require(seqVar, 4)); // start -> 0 -> 2 -> 4 -> 7 -> end
        int[] member = new int[]{start, 0, 2, 4, 7, end};
        int[] possible = new int[]{1, 3, 5, 6};
        int[] excluded = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member, possible, excluded);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testCannotForceInsertions(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        cp.post(new Precedence(seqVar, 0, 2, 4, 7));
        seqVar.insert(start, 1); // sequence: start -> 1 -> end

        cp.post(new Require(seqVar, 0));

        int[] member = new int[]{start, 1, end};
        int[] required = new int[]{start, 1, end, 0};
        int[] possible = new int[]{2, 3, 4, 5, 6, 7};
        int[] excluded = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member, required, possible, excluded);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testRemovePredecessors(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        cp.post(new Precedence(seqVar, 0, 2, 4, 7));
        seqVar.insert(start, 2);
        // seqVar: start -> 2 -> end
        cp.fixPoint();
        int[][] inserts1 = new int[][]{
                {start}, // 0 cannot have 2 as predecessor
                {start, 2},
                {},
                {start, 2},
                {2}, // 4 cannot have start as predecessor
                {start, 2},
                {start, 2},
                {2}, // 7 cannot have start as predecessor
                {}, // start
                {}  // end
        };
        int[] member1 = new int[]{start, 2, end};
        int[] possible1 = new int[]{1, 3, 5, 6, 0, 4, 7};
        int[] excluded1 = new int[]{};

        CPSeqVarAssertion.assertSeqVar(seqVar, member1, possible1, excluded1, inserts1);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testExcludeMustNotAppear(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        StateManager sm = cp.getStateManager();
        cp.post(new Precedence(seqVar, 0, 2, 4));
        sm.saveState();
        seqVar.exclude(0);
        cp.fixPoint();
        assertFalse(seqVar.isNode(2, EXCLUDED));
        assertFalse(seqVar.isNode(4, EXCLUDED));
        sm.restoreState();
        sm.saveState();
        seqVar.exclude(2);
        cp.fixPoint();
        assertFalse(seqVar.isNode(0, EXCLUDED));
        assertFalse(seqVar.isNode(4, EXCLUDED));
        sm.restoreState();
        seqVar.exclude(4);
        cp.fixPoint();
        assertFalse(seqVar.isNode(0, EXCLUDED));
        assertFalse(seqVar.isNode(2, EXCLUDED));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testInsertionReverseOrder(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        // insert the nodes in reverse order
        cp.post(new Precedence(seqVar, 0, 2, 4, 7));
        int[] member1 = new int[]{start, end};
        int[] possible1 = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
        int[] excluded1 = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member1, possible1, excluded1);

        seqVar.insert(start, 7);
        // seqVar: start -> 7 -> end
        cp.fixPoint();
        int[][] inserts2 = new int[][]{
                {start}, // 0 cannot have 7 as predecessor
                {start, 7},
                {start}, // 2 cannot have 4 nor 7 as predecessor
                {start, 7},
                {start}, // 4 cannot have 7 as predecessor
                {start, 7},
                {start, 7},
                {}, // member
                {}, // start
                {}  // end
        };
        int[] member2 = new int[]{start, 7, end};
        int[] possible2 = new int[]{0, 1, 2, 3, 4, 5, 6};
        int[] excluded2 = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member2, possible2, excluded2, inserts2);

        seqVar.insert(start, 4);
        seqVar.insert(start, 3);
        seqVar.insert(4, 1);
        // seqVar: start -> 3 -> 4 -> 1 -> 7 -> end
        cp.fixPoint();
        int[][] inserts3 = new int[][]{
                {start, 3},
                {}, // member
                {start, 3},
                {}, // member
                {}, // member
                {start, 3, 4, 1, 7},
                {start, 3, 4, 1, 7},
                {}, // member
                {}, // start
                {}  // end
        };
        int[] member3 = new int[]{start, 3, 4, 1, 7, end};
        int[] possible3 = new int[]{0, 2, 5, 6};
        int[] excluded3 = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member3, possible3, excluded3, inserts3);

        seqVar.insert(start, 2);
        // seqVar: start -> 2 -> 3 -> 4 -> 1 -> 7 -> end
        cp.fixPoint();
        int[][] inserts4 = new int[][]{
                {start},
                {}, // member
                {}, // member
                {}, // member
                {}, // member
                {start, 2, 3, 4, 1, 7},
                {start, 2, 3, 4, 1, 7},
                {}, // member
                {}, // start
                {}  // end
        };
        int[] member4 = new int[]{start, 2, 3, 4, 1, 7, end};
        int[] possible4 = new int[]{0, 5, 6};
        int[] excluded4 = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member4, possible4, excluded4, inserts4);

        seqVar.insert(start, 0);
        // seqVar: start -> 0 -> 2 -> 3 -> 4 -> 1 -> 7 -> end
        cp.fixPoint();
        int[][] inserts5 = new int[][]{
                {}, // member
                {}, // member
                {}, // member
                {}, // member
                {}, // member
                {start, 0, 2, 3, 4, 1, 7},
                {start, 0, 2, 3, 4, 1, 7},
                {}, // member
                {}, // start
                {}  // end
        };
        int[] member5 = new int[]{start, 0, 2, 3, 4, 1, 7, end};
        int[] possible5 = new int[]{5, 6};
        int[] excluded5 = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member5, possible5, excluded5, inserts5);
    }

    /**
     * train1 if inserting a node not in the order array within the seqVar changes correctly the insertions points
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void removeIntermediateInsertions1(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        cp.post(new Precedence(seqVar, 0, 2, 4, 7));

        seqVar.insert(start, 2);
        seqVar.insert(start, 5);
        // seqVar: start -> 5 -> 2 -> end
        cp.fixPoint();

        int[][] inserts1 = new int[][]{
                {start, 5}, // 0 cannot have 2, 4 nor 7 as predecessor
                {start, 5, 2},
                {},
                {start, 5, 2},
                {2}, // 4 cannot have start nor 2 as predecessor
                {}, // member
                {start, 5, 2},
                {2},
                {}, // start
                {}  // end
        };
        int[] member1 = new int[]{start, 5, 2, end};
        int[] possible1 = new int[]{0, 1, 3, 4, 6, 7};
        int[] excluded1 = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member1, possible1, excluded1, inserts1);

        seqVar.insert(2, 3);
        // seqVar: start -> 5 -> 2 -> 3 -> end
        cp.fixPoint();

        int[][] inserts2 = new int[][]{
                {start, 5},
                {start, 5, 2, 3},
                {},
                {},
                {2, 3},
                {}, // member
                {start, 5, 2, 3},
                {2, 3},
                {}, // start
                {}  // end
        };
        int[] member2 = new int[]{start, 5, 2, 3, end};
        int[] possible2 = new int[]{0, 1, 4, 6, 7};
        int[] excluded2 = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member2, possible2, excluded2, inserts2);

        seqVar.insert(3, 7);
        // seqVar: start -> 5 -> 2 -> 3 -> 7 end
        cp.fixPoint();

        int[][] preds3 = new int[][]{
                {start, 5},
                {start, 5, 2, 3, 7},
                {},
                {},
                {2, 3},
                {}, // member
                {start, 5, 2, 3, 7},
                {},
                {}, // start
                {}  // end
        };
        int[] member3 = new int[]{start, 5, 2, 3, 7, end};
        int[] possible3 = new int[]{0, 1, 4, 6};
        int[] excluded3 = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member3, possible3, excluded3, preds3);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void removeIntermediateInsertions2(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        cp.post(new Precedence(seqVar, 0, 2, 4, 7));

        seqVar.insert(start, 4);
        seqVar.insert(start, 5);
        seqVar.insert(4, 1);
        // seqVar: start -> 5 -> 4 -> 1 -> end
        cp.fixPoint();

        int[][] preds1 = new int[][]{
                {start, 5}, // 0 cannot have 4 nor 1 as predecessor
                {}, // member
                {start, 5}, // 2 cannot have 4 nor 1 as predecessor
                {start, 5, 4, 1},
                {}, // member, 4 cannot have 7 as predecessor
                {}, // member
                {start, 5, 4, 1},
                {4, 1},  // 7 cannot
                {}, // start
                {}  // end
        };
        int[] member1 = new int[]{start, 5, 4, 1, end};
        int[] required1 = new int[]{start, end, 4, 1, 5};
        int[] possible1 = new int[]{3, 6, 0, 2, 7};
        int[] excluded1 = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member1, required1, possible1, excluded1, preds1);
    }

    // train1 when only 2 nodes are in the order array, as the implementation might be a bit different
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testPrecedence2(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        cp.post(new Precedence(seqVar, 0, 1));

        seqVar.insert(start, 4); // not in order
        seqVar.insert(start, 1); // in order
        seqVar.insert(start, 5); // not in order
        // seqVar: start -> 5 -> 1 -> 4 -> end
        cp.fixPoint();

        int[][] inserts1 = new int[][]{
                {start, 5},
                {}, // member
                {start, 5, 1, 4},
                {start, 5, 1, 4},
                {}, // member
                {}, // member
                {start, 5, 1, 4},
                {start, 5, 1, 4},
                {}, // start
                {}  // end
        };
        int[] member1 = new int[]{start, 5, 1, 4, end};
        int[] possible1 = new int[]{0, 2, 3, 6, 7};
        int[] excluded1 = new int[]{};
        CPSeqVarAssertion.assertSeqVar(seqVar, member1, possible1, excluded1, inserts1);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void removeIntraOrderInvalidPred2(CPSolver cp) {
        int start = 19;
        int end = 20;
        CPSeqVar seqVar = CPFactory.makeSeqVar(cp, 21, start, end);
        seqVar.insert(start, 2);
        seqVar.insert(2, 3);
        seqVar.insert(3, 6);
        seqVar.insert(6, 12);
        seqVar.insert(12, 13);
        seqVar.insert(13, 14);
        seqVar.insert(14, 16);
        seqVar.insert(14, 16);
        seqVar.insert(16, 15);
        // seqVar: 19 -> 2 -> 3 -> 6 -> 12 -> 13 -> 14 -> 16 -> 15 -> 20
        int[] order = IntStream.range(0, 10).map(i -> i * 2).toArray(); // all even nodes from 0 to 18, both included
        cp.post(new Precedence(seqVar, order));
        Set<Integer> members = new HashSet<>();
        int n = seqVar.start();
        while (n != seqVar.end()) {
            members.add(n);
            n = seqVar.memberAfter(n);
        }
        members.add(end);
        Map<Integer, Set<Integer>> invalidMemberPred = Map.of(
                0, Arrays.stream(new int[]{2, 3, 6, 12, 13, 14, 16, 15}).boxed().collect(Collectors.toSet()),
                4, Arrays.stream(new int[]{start, 6, 12, 13, 14, 16, 15}).boxed().collect(Collectors.toSet()),
                8, Arrays.stream(new int[]{start, 2, 3, 12, 13, 14, 16, 15}).boxed().collect(Collectors.toSet()),
                10, Arrays.stream(new int[]{start, 2, 3, 12, 13, 14, 16, 15}).boxed().collect(Collectors.toSet()),
                18, Arrays.stream(new int[]{start, 2, 3, 6, 12, 13, 14,}).boxed().collect(Collectors.toSet())
        );
        Map<Integer, Set<Integer>> invalidMemberSucc = Map.of(
                0, Arrays.stream(new int[]{3, 6, 12, 13, 14, 16, 15, end}).boxed().collect(Collectors.toSet()),
                4, Arrays.stream(new int[]{2, 12, 13, 14, 16, 15, end}).boxed().collect(Collectors.toSet()),
                8, Arrays.stream(new int[]{2, 3, 6, 13, 14, 16, 15, end}).boxed().collect(Collectors.toSet()),
                10, Arrays.stream(new int[]{2, 3, 6, 13, 14, 16, 15, end}).boxed().collect(Collectors.toSet()),
                18, Arrays.stream(new int[]{2, 3, 6, 12, 13, 14, 16,}).boxed().collect(Collectors.toSet())
        );
        for (int node : invalidMemberPred.keySet()) {
            for (int pred : members) {
                if (pred != end) {
                    if (invalidMemberPred.get(node).contains(pred)) {
                        assertFalse(seqVar.hasEdge(pred, node), "edge (" + pred + "," + node + ") should not appear but does");
                    } else {
                        assertTrue(seqVar.hasEdge(pred, node), "edge (" + pred + "," + node + ") should appear but does not");
                    }
                }
            }
        }
        for (int node : invalidMemberSucc.keySet()) {
            for (int succ : members) {
                if (succ != start) {
                    if (invalidMemberSucc.get(node).contains(succ)) {
                        assertFalse(seqVar.hasEdge(node, succ), "edge (" + node + "," + succ + ") should not appear but does");
                    } else {
                        assertTrue(seqVar.hasEdge(node, succ), "edge (" + node + "," + succ + ") should appear but does not");
                    }
                }
            }
        }
    }


    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFindOneSol1(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        cp.post(new Precedence(seqVar, 0, 1, 2, 3, 4, 5, 6, 7));
        DFSearch search = CPFactory.makeDfs(cp, firstFail(seqVar));

        search.onSolution(() -> {
            if (seqVar.nNode(MEMBER) > 2) {
                int n = seqVar.memberAfter(seqVar.start()); // first node after start
                while (seqVar.memberAfter(n) != seqVar.end()) {
                    assertTrue(n < seqVar.memberAfter(n));
                    n = seqVar.memberAfter(n);
                }

            }
        });
        SearchStatistics stats = search.solve();
        // all sub-sequences of 0, 1, 2, 3, 4, 5, 6, 7 are solutions, there are 256
        assertEquals(256, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFindOneSol2(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        cp.post(new Precedence(seqVar, 8, 0, 1, 2, 3, 4, 5, 6, 7, 9));
        DFSearch search = CPFactory.makeDfs(cp, firstFail(seqVar));
        SearchStatistics stats = search.solve();
        assertEquals(256, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFindMultipleSol(CPSolver cp) {
        // set all nodes as required and attempt to find all solutions for a seqVar with a precedence and some remaining pending nodes
        // sub-seqVar that must appear: start -> 0 -> 1 -> 2 -> end
        // nodes 3 and 4 can be put anywhere in the seqVar
        int nInOrder = 3;
        int nRemaining = 2;
        int nNodes = nInOrder + nRemaining + 2;
        int start = nNodes - 2;
        int end = nNodes - 1;

        CPSeqVar seqVar = CPFactory.makeSeqVar(cp, nNodes, start, end);
        for (int node = 0; node < nNodes; ++node) {
            seqVar.require(node);
        }
        Random random = new Random(42);
        cp.post(new Precedence(seqVar, 0, 1, 2));
        int[] nodes = new int[seqVar.nNode()];
        DFSearch search = CPFactory.makeDfs(cp, () -> {
            if (seqVar.isFixed())
                return EMPTY;
            else {
                int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
                int node = nodes[random.nextInt(nInsertable)];
                int nInsert = seqVar.fillInsert(node, nodes);
                int insert = nodes[random.nextInt(nInsert)];
                return branch(() -> cp.post(new Insert(seqVar, insert, node)),
                        () -> cp.post(new RemoveDetour(seqVar, insert, node, seqVar.memberAfter(insert))));
            }
        });
        SearchStatistics stats = search.solve();
        // arrangements for nodes 3 and 4 within the seqVar
        assertEquals(8 + 4 * 3, stats.numberOfSolutions());
    }

}

