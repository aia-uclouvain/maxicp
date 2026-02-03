/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.state.StateManager;
import org.maxicp.util.exception.InconsistencyException;
import org.opentest4j.AssertionFailedError;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.cp.engine.core.CPSeqVarAssertion.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.firstFail;

public class CPSeqVarTest extends CPSolverTest {

    static int nNodes = 12;
    static int start = 10;
    static int end = 11;

    private static void resetPropagatorsArrays(boolean[] ... arr) {
        for (boolean[] array: arr) {
            Arrays.fill(array, false);
        }
    }

    private void resetPropagators(AtomicBoolean ... bs) {
        for (AtomicBoolean b: bs) {
            b.set(false);
        }
    }

    private static void assertIsBoolArrayTrueAt(boolean[] values, int... indexes) {
        Arrays.sort(indexes);
        int j = 0;
        int i = 0;
        for (; i < values.length && j < indexes.length; ++i) {
            if (i == indexes[j]) {
                assertTrue(values[i]);
                ++j;
            } else {
                assertFalse(values[i]);
            }
        }
        for (; i < values.length; ++i) {
            assertFalse(values[i]);
        }
    }

    public static Stream<CPSeqVar> getSeqVar() {
        return getSolver().map(cp -> CPFactory.makeSeqVar(cp, nNodes, start, end));
    }

    /**
     * Tests if the graph sequence var is constructed with the right number of nodes and insertions
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testConstructor(CPSeqVar seqVar) {
        int[] member = new int[]{start, end};
        int[] possible = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] excluded = new int[]{};
        assertSeqVar(seqVar, member, possible, excluded);
        // explicit call with all insertions. Should be the same assertion as above
        int[][] inserts = new int[][]{
                {start},
                {start},
                {start},
                {start},
                {start},
                {start},
                {start},
                {start},
                {start},
                {start},
                {},
                {}
        };
        assertSeqVar(seqVar, member, possible, excluded, inserts);
    }

    /**
     * Tests for the insertions of nodes within the sequence
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testInsert(CPSeqVar seqVar) {
        StateManager sm = seqVar.getSolver().getStateManager();
        sm.saveState();

        seqVar.insert(start, 0); // start -> 0 -> end;

        assertEquals(0, seqVar.memberAfter(start));
        assertEquals(0, seqVar.memberBefore(end));
        assertFalse(seqVar.hasEdge(start, end));
        assertTrue(seqVar.isNode(0, MEMBER));
        assertTrue(seqVar.isNode(2, INSERTABLE));
        assertTrue(seqVar.hasEdge(0, 2));
        assertTrue(seqVar.hasEdge(2, end));
        assertTrue(seqVar.hasInsert(0, 2));

        seqVar.insert(0, 2); // start -> 0 -> 2 -> end
        assertFalse(seqVar.hasEdge(0, end));
        assertFalse(seqVar.hasEdge(start, 2));
        assertFalse(seqVar.hasEdge(2, 0));

        int[] member1 = new int[]{start, 0, 2, end};
        int[] possible1 = new int[]{1, 3, 4, 5, 6, 7, 8, 9};
        int[] excluded1 = new int[]{};
        int[][] inserts1 = new int[][]{
                {},
                {0, 2, start},
                {},
                {0, 2, start},
                {0, 2, start},
                {0, 2, start},
                {0, 2, start},
                {0, 2, start},
                {0, 2, start},
                {0, 2, start},
                {},
                {}
        };
        assertSeqVar(seqVar, member1, possible1, excluded1, inserts1);

        sm.saveState();

        seqVar.insert(start, 5); // start -> 5 -> 0 -> 2 -> end

        assertFalse(seqVar.hasEdge(start, 0));
        assertFalse(seqVar.hasEdge(5, 2));
        assertFalse(seqVar.hasEdge(5, end));

        seqVar.insert(2, 7); // start -> 5 -> 0 -> 2 -> 7 -> end

        assertFalse(seqVar.hasEdge(2, end));
        assertFalse(seqVar.hasEdge(start, 7));
        assertFalse(seqVar.hasEdge(5, 7));
        assertFalse(seqVar.hasEdge(0, 7));

        int[] member2 = new int[]{start, 5, 0, 2, 7, end};
        int[] possible2 = new int[]{1, 3, 4, 6, 8, 9};
        int[] excluded2 = new int[]{};
        int[][] inserts2 = new int[][]{
                {},
                {start, 5, 0, 2, 7},
                {},
                {start, 5, 0, 2, 7},
                {start, 5, 0, 2, 7},
                {},
                {start, 5, 0, 2, 7},
                {},
                {start, 5, 0, 2, 7},
                {start, 5, 0, 2, 7},
                {},
                {}
        };
        assertSeqVar(seqVar, member2, possible2, excluded2, inserts2);

        sm.saveState();

        seqVar.insert(start, 1);
        seqVar.insert(7, 9);
        seqVar.insert(2, 6);
        seqVar.insert(1, 8);
        seqVar.insert(8, 3);
        seqVar.insert(2, 4); // start -> 1 -> 8 -> 3 -> 5 -> 0 -> 2 -> 4 -> 6 -> 7 -> 9 -> end

        int[] member3 = new int[]{start, 1, 8, 3, 5, 0, 2, 4, 6, 7, 9, end};
        int[] possible3 = new int[]{};
        int[] excluded3 = new int[]{};
        int[][] inserts3 = new int[][]{
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
        };
        assertSeqVar(seqVar, member3, possible3, excluded3, inserts3);

        sm.restoreState();
        assertSeqVar(seqVar, member2, possible2, excluded2, inserts2);
        sm.restoreState();
        assertSeqVar(seqVar, member1, possible1, excluded1, inserts1);
    }

    /**
     * Tests for the exclusion of nodes within the sequence
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testExclude(CPSeqVar seqVar) {
        StateManager sm = seqVar.getSolver().getStateManager();
        seqVar.exclude(8);
        int[] member1 = new int[]{start, end};
        int[] possible1 = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 9};
        int[] excluded1 = new int[]{8};
        assertSeqVar(seqVar, member1, possible1, excluded1);

        sm.saveState();

        seqVar.exclude(2);
        seqVar.exclude(0);
        int[] member2 = new int[]{start, end};
        int[] possible2 = new int[]{1, 3, 4, 5, 6, 7, 9};
        int[] excluded2 = new int[]{8, 2, 0};
        assertSeqVar(seqVar, member2, possible2, excluded2);

        sm.saveState();

        seqVar.exclude(1);
        seqVar.exclude(9);
        seqVar.exclude(4);
        seqVar.exclude(5);
        seqVar.exclude(7);
        seqVar.exclude(6);
        seqVar.exclude(3);
        int[] member3 = new int[]{start, end};
        int[] possible3 = new int[]{};
        int[] excluded3 = new int[]{8, 2, 0, 1, 3, 4, 5, 6, 7, 9};
        assertSeqVar(seqVar, member3, possible3, excluded3);

        sm.restoreState();
        assertSeqVar(seqVar, member2, possible2, excluded2);
        sm.restoreState();
        assertSeqVar(seqVar, member1, possible1, excluded1);
    }

    /**
     * Tests for the removal of subsequences
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testNotBetween(CPSeqVar seqVar) {
        StateManager sm = seqVar.getSolver().getStateManager();
        seqVar.notBetween(start, 2, seqVar.memberAfter(start));
        seqVar.notBetween(start, 4, seqVar.memberAfter(start));
        // removing the insert has the side effect of excluding the nodes, as they don't have any insertion point anymore
        int[] member1 = new int[]{start, end};
        int[] possible1 = new int[]{0, 1, 3, 5, 6, 7, 8, 9};
        int[] excluded1 = new int[]{2, 4};

        int[][] inserts1 = new int[][]{
                {start},
                {start},
                {},
                {start},
                {},
                {start},
                {start},
                {start},
                {start},
                {start},
                {},
                {},
        };
        assertSeqVar(seqVar, member1, possible1, excluded1, inserts1);

        sm.saveState();

        seqVar.insert(start, 6); // the insertion means that removing only one insertion for a non-excluded node
        // does not exclude it directly

        seqVar.notBetween(start, 3, seqVar.memberAfter(start));
        seqVar.notBetween(start, 7, seqVar.memberAfter(start));
        seqVar.notBetween(6, 8, seqVar.memberAfter(6));

        int[] member2 = new int[]{start, 6, end};
        int[] possible2 = new int[]{0, 1, 3, 5, 7, 8, 9};
        int[] excluded2 = new int[]{2, 4};
        int[][] predInsert2 = new int[][] {
                {start, 6},
                {start, 6},
                {},
                {6},
                {},
                {start, 6},
                {},
                {6},
                {start},
                {start, 6},
                {},
                {},
        };

        assertSeqVar(seqVar, member2, possible2, excluded2, predInsert2);

        sm.saveState();

        seqVar.notBetween(6, 5, seqVar.memberAfter(6));
        seqVar.notBetween(start, 5, seqVar.memberAfter(start));
        seqVar.notBetween(start, 8, seqVar.memberAfter(start));
        seqVar.notBetween(start, 1, seqVar.memberAfter(start));
        seqVar.notBetween(6, 0, seqVar.memberAfter(6));

        int[] member3 = new int[]{start, 6, end};
        int[] possible3 = new int[]{0, 1, 3, 7, 9};
        int[] excluded3 = new int[]{2, 4, 8, 5};
        int[][] predInsert3 = new int[][] {
                {start},
                {6},
                {},
                {6},
                {},
                {},
                {},
                {6},
                {},
                {start, 6},
                {},
                {},
        };
        assertSeqVar(seqVar, member3, possible3, excluded3, predInsert3);

        sm.restoreState();
        assertSeqVar(seqVar, member2, possible2, excluded2, predInsert2);
        sm.restoreState();
        assertSeqVar(seqVar, member1, possible1, excluded1, inserts1);
    }

    /**
     * Tests for the removal of several sub-sequences with only one notBetween
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testRemoveNotBetweenFollowing(CPSeqVar seqVar) {
        StateManager sm = seqVar.getSolver().getStateManager();
        seqVar.insert(start, 1);
        seqVar.insert(1, 2);
        seqVar.insert(2, 3);
        // start -> 1 -> 2 -> 3 -> end
        seqVar.notBetween(1, 4, 3);
        int[] member1 = new int[]{start, 1, 2, 3, end};
        int[] possible1 = new int[]{0, 4, 5, 6, 7, 8, 9};
        int[] excluded1 = new int[]{};

        int[][] inserts1 = new int[][]{
                {start, 1, 2, 3},
                {},
                {},
                {},
                {start, 3},
                {start, 1, 2, 3},
                {start, 1, 2, 3},
                {start, 1, 2, 3},
                {start, 1, 2, 3},
                {start, 1, 2, 3},
                {},
                {},
        };
        assertSeqVar(seqVar, member1, possible1, excluded1, inserts1);
    }

    /**
     * Tests that removing all edges of one node ends up excluding it
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testRemoveAllInsertMeansExclude(CPSeqVar seqVar) {
        seqVar.insert(start, 0);
        seqVar.insert(0, 1);
        seqVar.insert(1, 2);
        seqVar.insert(2, 3);
        seqVar.insert(3, 4);

        int[] member1 = new int[]{start, 0, 1, 2, 3, 4, end};
        int[] possible1 = new int[]{5, 6, 7, 8, 9};
        int[] excluded1 = new int[]{};
        int[][] pred1 = new int[][]{
                {},
                {},
                {},
                {},
                {},
                {0, 1, 2, 3, 4, start},
                {0, 1, 2, 3, 4, start},
                {0, 1, 2, 3, 4, start},
                {0, 1, 2, 3, 4, start},
                {0, 1, 2, 3, 4, start},
                {},
                {}
        };

        int node1 = 5;
        int nRemoved = 0;
        for (int i = 0; i < member1.length - 1; ++i) {
            int predToRemove = member1[i];
            seqVar.notBetween(predToRemove, node1, seqVar.memberAfter(predToRemove));
            ++nRemoved;
            pred1[node1] = Arrays.stream(pred1[node1]).filter(j -> j != predToRemove).toArray();
            if (nRemoved < member1.length - 1) { // the maximum number of insertions that can be removed without excluding the node (nMember nodes - 2)
                assertSeqVar(seqVar, member1, possible1, excluded1, pred1);
            }
        }
        int[] member2 = new int[]{start, 0, 1, 2, 3, 4, end};
        int[] possible2 = new int[]{6, 7, 8, 9};
        int[] excluded2 = new int[]{node1};
        assertSeqVar(seqVar, member2, possible2, excluded2);

        int[][] pred2 = new int[][]{
                {},
                {},
                {},
                {},
                {},
                {},
                {0, 1, 2, 3, 4, start},
                {0, 1, 2, 3, 4, start},
                {0, 1, 2, 3, 4, start},
                {0, 1, 2, 3, 4, start},
                {},
                {}
        };

        int node2 = 6;
        nRemoved = 0;
        for (int i = 0; i < member2.length - 1; ++i) {
            int predToRemove = member2[i];
            seqVar.notBetween(predToRemove, node2, seqVar.memberAfter(predToRemove));
            ++nRemoved;
            pred2[node2] = Arrays.stream(pred2[node2]).filter(j -> j != predToRemove).toArray();
            if (nRemoved < member1.length - 1) { // the maximum number of insertions that can be removed without excluding the node (nMember nodes - 2)
                assertSeqVar(seqVar, member2, possible2, excluded2, pred2);
            }
        }
        int[] member3 = new int[]{start, 0, 1, 2, 3, 4, end};
        int[] possible3 = new int[]{7, 8, 9};
        int[] excluded3 = new int[]{node1, node2};
        assertSeqVar(seqVar, member3, possible3, excluded3);

    }

    /**
     * Tests for both exclusions and insertions within the sequence
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testExcludeAndInsert(CPSeqVar seqVar) {
        StateManager sm = seqVar.getSolver().getStateManager();
        seqVar.insert(start, 0);
        seqVar.insert(0, 2);
        seqVar.exclude(5);
        // sequence at this point: begin -> 0 -> 2 -> end
        int[] member1 = new int[]{start, 0, 2, end};
        int[] possible1 = new int[]{1, 3, 4, 6, 7, 8, 9};
        int[] excluded1 = new int[]{5};
        assertSeqVar(seqVar, member1, possible1, excluded1);

        sm.saveState();

        seqVar.insert(0, 4);
        seqVar.insert(start, 9);
        seqVar.exclude(7);
        // sequence at this point: begin -> 9 -> 0 -> 4 -> 2 -> end
        int[] member2 = new int[]{start, 9, 0, 4, 2, end};
        int[] possible2 = new int[]{1, 3, 6, 8};
        int[] excluded2 = new int[]{5, 7};
        assertSeqVar(seqVar, member2, possible2, excluded2);

        sm.saveState();

        seqVar.insert(4, 3);
        seqVar.exclude(6);
        seqVar.insert(2, 1);
        // sequence at this point: begin -> 9 -> 0 -> 4 -> 2 -> end
        int[] member3 = new int[]{start, 9, 0, 4, 3, 2, 1, end};
        int[] possible3 = new int[]{8};
        int[] excluded3 = new int[]{5, 7, 6};
        assertSeqVar(seqVar, member3, possible3, excluded3);

        sm.restoreState();
        assertSeqVar(seqVar, member2, possible2, excluded2);
        sm.restoreState();
        assertSeqVar(seqVar, member1, possible1, excluded1);
    }

    /**
     * Tests for both exclusions and removal of edges within the sequence
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testInsertAndRemoveEdge(CPSeqVar seqVar) {
        seqVar.insert(start, 0);
        seqVar.insert(0, 2);
        seqVar.notBetween(0, 5, seqVar.memberAfter(0));
        seqVar.notBetween(start, 7, seqVar.memberAfter(start));
        seqVar.notBetween(2, 8, seqVar.memberAfter(2));
        seqVar.notBetween(0, 3, seqVar.memberAfter(0));
        // sequence at this point: begin -> 0 -> 2 -> end
        int[] member1 = new int[]{start, 0, 2, end};
        int[] possible1 = new int[]{1, 3, 4, 5, 6, 7, 8, 9};
        int[] excluded1 = new int[]{};

        int[][] predInsert1 = new int[][]{
                {},
                {start, 0, 2},
                {},
                {start, 2},
                {start, 0, 2},
                {start, 2},
                {start, 0, 2},
                {0, 2},
                {start, 0},
                {start, 0, 2},
                {},
                {}
        };
        assertSeqVar(seqVar, member1, possible1, excluded1, predInsert1);
    }

    /**
     * Test for the requirement of nodes within the sequence
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testRequire(CPSeqVar seqVar) {
        StateManager sm = seqVar.getSolver().getStateManager();

        int[] member = new int[]{start, end};
        int[] possible = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] excluded = new int[]{};
        assertSeqVar(seqVar, member, possible, excluded);

        sm.saveState();

        seqVar.require(7);
        // requiring the node has forced its insertion because it had only one place to do so
        int[] member0 = new int[]{start, 7, end};
        int[] required0 = new int[]{start, end, 7};
        int[] possible0 = new int[]{0, 1, 2, 3, 4, 5, 6, 8, 9};
        int[] excluded0 = new int[]{};
        assertSeqVar(seqVar, member0, required0, possible0, excluded0);

        sm.saveState();

        seqVar.require(3);
        seqVar.require(0);
        seqVar.require(1);
        // the other nodes required cannot be directly inserted: they have multiple positions
        int[] member1 = new int[]{start, 7, end};
        int[] required1 = new int[]{start, end, 7, 3, 0, 1};
        int[] possible1 = new int[]{2, 4, 5, 6, 8, 9};
        int[] excluded1 = new int[]{};
        assertSeqVar(seqVar, member1, required1, possible1, excluded1);

        sm.saveState();

        seqVar.require(4);
        seqVar.require(5);
        seqVar.require(9);
        seqVar.require(2);
        int[] member2 = member1;
        int[] required2 = new int[]{start, end, 7, 3, 0, 1, 4, 5, 9, 2};
        int[] possible2 = new int[]{6, 8};
        int[] excluded2 = excluded1;
        assertSeqVar(seqVar, member2, required2, possible2, excluded2);

        sm.saveState();

        seqVar.require(6);
        seqVar.require(8);
        int[] member3 = member1;
        int[] required3 = new int[]{start, end, 7, 3, 0, 1, 4, 5, 9, 2, 6, 8};
        int[] possible3 = new int[]{};
        int[] excluded3 = excluded1;
        assertSeqVar(seqVar, member3, required3, possible3, excluded3);
        assertFalse(seqVar.isFixed());

        sm.restoreState();
        assertSeqVar(seqVar, member2, required2, possible2, excluded2);
        sm.restoreState();
        assertSeqVar(seqVar, member1, required1, possible1, excluded1);
        sm.restoreState();
        assertSeqVar(seqVar, member0, required0, possible0, excluded0);
        sm.restoreState();
        assertSeqVar(seqVar, member, possible, excluded);
    }

    /**
     * Tests for both exclusions and removal of insertions within the sequence
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testExcludeAndRemoveEdge(CPSeqVar seqVar) {
        StateManager sm = seqVar.getSolver().getStateManager();
        seqVar.exclude(3);
        seqVar.notBetween(start, 8, seqVar.memberAfter(start));
        int[] member1 = new int[]{start, end};
        int[] possible1 = new int[]{0, 1, 2, 4, 5, 6, 7, 9};
        int[] excluded1 = new int[]{3, 8};
        int[][] pred1 = new int[][]{
                {start},
                {start},
                {start},
                {},
                {start},
                {start},
                {start},
                {start},
                {},
                {start},
                {},
                {}
        };
        assertSeqVar(seqVar, member1, possible1, excluded1, pred1);

        sm.saveState();

        // make the sequence grow to test additional scenarios
        seqVar.insert(start, 4); // start -> 4 -> end
        seqVar.insert(start, 7); // start -> 7 -> 4 -> end

        seqVar.notBetween(start, 0, seqVar.memberAfter(start));
        seqVar.exclude(2);
        seqVar.notBetween(7, 6, seqVar.memberAfter(7));
        seqVar.notBetween(start, 6, seqVar.memberAfter(start));
        seqVar.exclude(8); // 8 is already excluded. this should do nothing
        seqVar.notBetween(start, 3, seqVar.memberAfter(start)); // 3 is already excluded. this should do nothing
        seqVar.notBetween(4, 5, seqVar.memberAfter(4));
        int[][] pred2 = new int[][]{
                {7, 4},
                {start, 7, 4},
                {},
                {},
                {},
                {start, 7},
                {4},
                {},
                {},
                {start, 7, 4},
                {},
                {}
        };
        int[] member2 = new int[]{start, 7, 4, end};
        int[] possible2 = new int[]{0, 1, 5, 6, 9};
        int[] excluded2 = new int[]{2, 3, 8};
        assertSeqVar(seqVar, member2, possible2, excluded2, pred2);

        sm.saveState();

        seqVar.notBetween(4, 6, seqVar.memberAfter(4)); // this end up excluding node 6
        seqVar.exclude(9);
        seqVar.exclude(5);
        seqVar.notBetween(start, 1, seqVar.memberAfter(start));
        int[][] pred3 = new int[][]{
                {7, 4},
                {7, 4},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {}
        };
        int[] member3 = new int[]{start, 7, 4, end};
        int[] possible3 = new int[]{0, 1};
        int[] excluded3 = new int[]{2, 3, 5, 6, 8, 9};
        assertSeqVar(seqVar, member3, possible3, excluded3, pred3);

        sm.saveState();

        // exclude node 0 through the removal of all its related edges
        seqVar.notBetween(4, 0, seqVar.memberAfter(4));
        seqVar.notBetween(7, 0, seqVar.memberAfter(7));
        seqVar.exclude(1);
        int[] member4 = new int[]{start, 7, 4, end};
        int[] possible4 = new int[]{};
        int[] excluded4 = new int[]{0, 1, 2, 3, 5, 6, 8, 9};
        assertSeqVar(seqVar, member4, possible4, excluded4);

        sm.restoreState();
        assertSeqVar(seqVar, member3, possible3, excluded3, pred3);
        sm.restoreState();
        assertSeqVar(seqVar, member2, possible2, excluded2, pred2);
        sm.restoreState();
        assertSeqVar(seqVar, member1, possible1, excluded1, pred1);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testCloseTransitions(CPSeqVar seqVar) {
        StateManager sm = seqVar.getSolver().getStateManager();
        seqVar.insert(start, 5); // start -> 5 -> end
        seqVar.notBetween(start, 2, seqVar.memberAfter(start));
        // node 2 can never be inserted somewhere between start and 5
        int[] member1 = new int[]{start, 5, end};
        int[] possible1 = new int[]{0, 1, 2, 3, 4, 6, 7, 8, 9};
        int[] excluded1 = new int[]{};
        int[][] pred1 = new int[][]{
                {start, 5},
                {start, 5},
                {5},
                {start, 5},
                {start, 5},
                {},
                {start, 5},
                {start, 5},
                {start, 5},
                {start, 5},
                {},
                {}
        };

        assertSeqVar(seqVar, member1, possible1, excluded1, pred1);

        sm.saveState();

        seqVar.insert(start, 1);  // start -> 1 -> 5 -> end
        seqVar.insert(start, 3);  // start -> 3 -> 1 -> 5 -> end

        seqVar.insert(5, 7);  // start -> 3 -> 1 -> 5 -> 7 -> end
        seqVar.insert(5, 8); // start -> 3 -> 1 -> 5 -> 8 -> 7 -> end

        int[] member2 = new int[]{start, 3, 1, 5, 8, 7, end};
        int[] possible2 = new int[]{0, 2, 4, 6, 9};
        int[] excluded2 = new int[]{};
        int[][] pred2 = new int[][]{
                {start, 3, 1, 5, 8, 7},
                {},
                {5, 8, 7}, // node 2 can never be inserted somewhere between start and 5
                {},
                {start, 3, 1, 5, 8, 7},
                {},
                {start, 3, 1, 5, 8, 7},
                {},
                {},
                {start, 3, 1, 5, 8, 7},
                {},
                {}
        };
        assertSeqVar(seqVar, member2, possible2, excluded2, pred2);
        sm.saveState();

        seqVar.insert(1, 0);  // start -> 3 -> 1 -> 0 -> 5 -> 8 -> 7 -> end
        seqVar.insert(3, 4);  // start -> 3 -> 4 -> 1 -> 0 -> 5 -> 8 -> 7 -> end

        seqVar.insert(8, 6);  // start -> 3 -> 4 -> 1 -> 0 -> 5 -> 8 -> 6 -> 7 -> end
        seqVar.insert(7, 9);  // start -> 3 -> 4 -> 1 -> 0 -> 5 -> 8 -> 6 -> 7 -> 9 -> end

        int[] member3 = new int[]{start, 3, 4, 1, 0, 5, 8, 6, 7, 9, end};
        int[] possible3 = new int[]{2};
        int[] excluded3 = new int[]{};
        int[][] pred3 = new int[][]{
                {},
                {},
                {5, 8, 6, 7, 9}, // node 2 can never be inserted somewhere between start and 5
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {}
        };
        assertSeqVar(seqVar, member3, possible3, excluded3, pred3);

        sm.restoreState();
        assertSeqVar(seqVar, member2, possible2, excluded2, pred2);
        sm.restoreState();
        assertSeqVar(seqVar, member1, possible1, excluded1, pred1);
    }

    /**
     * More complicated test! It checks for a mix of several operations, as well as trying to do some invalid operations
     * <p>
     * Tests for a mix of
     * - Insertions
     * - Require
     * - Exclusions
     * - Removal of edges
     * <p>
     * Also tests for
     * - Exclusion for inserted / required node, throwing an {@link InconsistencyException}
     * - Exclusion of node because all its edges have been removed
     * - Removal of all edges of a required node, throwing an {@link InconsistencyException}
     * - State recovery from {@link StateManager#restoreState()}
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testChangeStateOperations(CPSeqVar seqVar) {
        StateManager sm = seqVar.getSolver().getStateManager();
        seqVar.require(8); // start -> 8 -> end
        seqVar.exclude(9);
        seqVar.notBetween(8, 3, seqVar.memberAfter(8));
        seqVar.insert(start, 2); // start -> 2 -> 8 -> end
        seqVar.require(6);

        int[] member1 = new int[]{start, 2, 8, end};
        int[] required1 = new int[]{start, 2, 8, end, 6};
        int[] possible1 = new int[]{0, 1, 3, 4, 5, 7};
        int[] excluded1 = new int[]{9};
        int[][] pred1 = new int[][]{
                {start, 2, 8},
                {start, 2, 8},
                {},
                {start, 2},
                {start, 2, 8},
                {start, 2, 8},
                {start, 2, 8},
                {start, 2, 8},
                {},
                {},
                {},
                {}
        };
        assertSeqVar(seqVar, member1, required1, possible1, excluded1, pred1);

        sm.saveState();

        seqVar.insert(2, 4); // start -> 2 -> 4 -> 8 -> end
        seqVar.exclude(0);
        seqVar.require(7);
        seqVar.require(3);
        seqVar.notBetween(start, 5, seqVar.memberAfter(start));
        seqVar.notBetween(4, 7, seqVar.memberAfter(4));
        seqVar.notBetween(2, 7, seqVar.memberAfter(2));

        int[] member2 = new int[]{start, 2, 4, 8, end};
        int[] required2 = new int[]{start, 2, 4, 8, end, 3, 7, 6};
        int[] possible2 = new int[]{1, 5};
        int[] excluded2 = new int[]{9, 0};
        int[][] pred2 = new int[][]{
                {},
                {start, 2, 4, 8},
                {},
                {start, 2, 4},
                {},
                {2, 4, 8},
                {start, 2, 4, 8},
                {start, 8},
                {},
                {},
                {},
                {}
        };
        assertSeqVar(seqVar, member2, required2, possible2, excluded2, pred2);

        sm.saveState();

        seqVar.notBetween(start, 1, seqVar.memberAfter(start));
        seqVar.notBetween(8, 1, seqVar.memberAfter(8));
        seqVar.insert(2, 6); // start -> 2 -> 6 -> 4 -> 8 -> end
        seqVar.insert(start, 7);  // start -> 7 -> 2 -> 6 -> 4 -> 8 -> end
        seqVar.exclude(5);

        int[] member3 = new int[]{start, 7, 2, 6, 4, 8, end};
        int[] required3 = new int[]{start, 7, 2, 6, 4, 8, end, 3};
        int[] possible3 = new int[]{1};
        int[] excluded3 = new int[]{9, 0, 5};
        int[][] pred3 = new int[][]{
                {},
                {2, 4, 6},
                {},
                {start, 7, 2, 4, 6},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {}
        };
        assertSeqVar(seqVar, member3, required3, possible3, excluded3, pred3);

        sm.saveState();

        /* now the fun begins! Test for:
         *  - Exclusion for inserted / required node, throwing an {@link InconsistencyException}
         *  - Exclusion of node because all its insertions have been removed
         *  - Removal of all edges of a required node, forcing its insertion
         *  - State recovery from {@link StateManager#restoreState()}
         */

        int j = 0;
        for (Runnable invalidOp : new Runnable[]{
                () -> seqVar.exclude(2), // exclude an inserted node
                () -> seqVar.exclude(3), // exclude a required node
        }) {
            assertSeqVar(seqVar, member3, required3, possible3, excluded3, pred3);
            sm.saveState();
            assertThrowsExactly(InconsistencyException.class, () -> seqVar.exclude(3), //invalidOp::call,
                    "An invalid exclusion occurred without throwing an Inconsistency");
            sm.restoreState(); // goes back before the invalid operation
        }

        // remove all predecessors for node 1, excluding it
        for (int pred : new int[]{2, 4, 6}) { // note that the removal of node 5 should have no effect
            assertFalse(seqVar.isNode(1, EXCLUDED)); // the node is not excluded yet!
            seqVar.notBetween(pred, 1, seqVar.memberAfter(pred));
        }

        int[] member4 = new int[]{start, 7, 2, 6, 4, 8, end};
        int[] required4 = new int[]{start, 7, 2, 6, 4, 8, end, 3};
        int[] possible4 = new int[]{};
        int[] excluded4 = new int[]{1, 9, 0, 5};
        int[][] pred4 = new int[][]{
                {},
                {},
                {},
                {start, 7, 2, 4, 6},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {}
        };
        assertSeqVar(seqVar, member4, required4, possible4, excluded4, pred4);

        // remove all edges except one for a required node. This will insert it
        sm.saveState();
        for (int pred : new int[]{start, 7, 2, 6}) {
            seqVar.notBetween(pred, 3, seqVar.memberAfter(pred));
        }
        assertTrue(seqVar.isNode(3, MEMBER));
        sm.restoreState();
        assertSeqVar(seqVar, member4, required4, possible4, excluded4, pred4);

        // insert all remaining nodes that are not yet inserted
        sm.saveState();
        seqVar.insert(start, 3);
        int[] member5 = new int[]{start, 3, 7, 2, 6, 4, 8, end};
        int[] required5 = new int[]{start, 3, 7, 2, 6, 4, 8, end};
        int[] possible5 = new int[]{};
        int[] excluded5 = new int[]{1, 9, 0, 5};
        assertSeqVar(seqVar, member5, required5, possible5, excluded5);

        sm.restoreState();
        assertSeqVar(seqVar, member4, required4, possible4, excluded4, pred4);
        sm.restoreState();
        assertSeqVar(seqVar, member3, required3, possible3, excluded3, pred3);
        sm.restoreState();
        assertSeqVar(seqVar, member2, required2, possible2, excluded2, pred2);
        sm.restoreState();
        assertSeqVar(seqVar, member1, required1, possible1, excluded1, pred1);

    }

    /**
     * Tests if the propagation is triggered correctly by the {@link CPNodeVar}
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testPropagationNode(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        boolean[] propagateInsertArrCalled = new boolean[nNodes];
        boolean[] propagateInsertRemovedArrCalled = new boolean[nNodes];
        boolean[] propagateExcludeArrCalled = new boolean[nNodes];
        boolean[] propagateRequireArrCalled = new boolean[nNodes];

        CPNodeVar[] nodeVars = new CPNodeVar[nNodes];
        for (int i = 0; i < nNodes; ++i) {
            nodeVars[i] = seqVar.getNodeVar(i);
        }

        CPConstraint cons = new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                for (int i = 0; i < nNodes; ++i) {
                    int finalI = i;
                    nodeVars[i].whenInsert(() -> propagateInsertArrCalled[finalI] = true);
                    nodeVars[i].whenInsertRemoved(() -> propagateInsertRemovedArrCalled[finalI] = true);
                    nodeVars[i].whenExclude(() -> propagateExcludeArrCalled[finalI] = true);
                    nodeVars[i].whenRequire(() -> propagateRequireArrCalled[finalI] = true);
                }
            }
        };
        cp.post(cons);
        seqVar.insert(seqVar.start(), 9); // sequence= start -> 9 -> end
        cp.fixPoint();
        assertIsBoolArrayTrueAt(propagateInsertArrCalled, 9);
        assertIsBoolArrayTrueAt(propagateRequireArrCalled, 9);
        assertIsBoolArrayTrueAt(propagateExcludeArrCalled);
        assertIsBoolArrayTrueAt(propagateInsertRemovedArrCalled);
        resetPropagatorsArrays(propagateInsertArrCalled, propagateRequireArrCalled, propagateInsertRemovedArrCalled, propagateExcludeArrCalled);

        seqVar.exclude(5);
        cp.fixPoint();
        assertIsBoolArrayTrueAt(propagateInsertArrCalled);
        assertIsBoolArrayTrueAt(propagateRequireArrCalled);
        assertIsBoolArrayTrueAt(propagateExcludeArrCalled, 5);
        resetPropagatorsArrays(propagateInsertArrCalled, propagateRequireArrCalled, propagateInsertRemovedArrCalled, propagateExcludeArrCalled);

        seqVar.insert(seqVar.start(), 2); // start -> 2 -> 9 -> end
        seqVar.insert(seqVar.start(), 8); // start -> 8 -> 2 -> 9 -> end
        cp.fixPoint();
        assertIsBoolArrayTrueAt(propagateInsertArrCalled, 2, 8);
        assertIsBoolArrayTrueAt(propagateRequireArrCalled, 2, 8);
        assertIsBoolArrayTrueAt(propagateExcludeArrCalled);
        assertIsBoolArrayTrueAt(propagateInsertRemovedArrCalled);
        resetPropagatorsArrays(propagateInsertArrCalled, propagateRequireArrCalled, propagateInsertRemovedArrCalled, propagateExcludeArrCalled);

        seqVar.exclude(3);
        cp.fixPoint();
        assertIsBoolArrayTrueAt(propagateInsertArrCalled);
        assertIsBoolArrayTrueAt(propagateExcludeArrCalled, 3);
        assertIsBoolArrayTrueAt(propagateRequireArrCalled);
        assertIsBoolArrayTrueAt(propagateInsertRemovedArrCalled);
        resetPropagatorsArrays(propagateInsertArrCalled, propagateRequireArrCalled, propagateInsertRemovedArrCalled, propagateExcludeArrCalled);

        seqVar.notBetween(start, 1, seqVar.memberAfter(start));
        seqVar.notBetween(2, 7, seqVar.memberAfter(2));
        seqVar.notBetween(8, 7, seqVar.memberAfter(8)); // start -> 8 -> 2 -> 9 -> end
        cp.fixPoint();
        assertIsBoolArrayTrueAt(propagateInsertArrCalled);
        assertIsBoolArrayTrueAt(propagateRequireArrCalled);
        assertIsBoolArrayTrueAt(propagateExcludeArrCalled);
        assertIsBoolArrayTrueAt(propagateInsertRemovedArrCalled, 1, 7);
        resetPropagatorsArrays(propagateInsertArrCalled, propagateRequireArrCalled, propagateInsertRemovedArrCalled, propagateExcludeArrCalled);

        seqVar.require(0);
        seqVar.require(4);
        cp.fixPoint();
        assertIsBoolArrayTrueAt(propagateInsertArrCalled);
        assertIsBoolArrayTrueAt(propagateRequireArrCalled, 0, 4);
        assertIsBoolArrayTrueAt(propagateExcludeArrCalled);
        assertIsBoolArrayTrueAt(propagateInsertRemovedArrCalled);
        resetPropagatorsArrays(propagateInsertArrCalled, propagateRequireArrCalled, propagateInsertRemovedArrCalled, propagateExcludeArrCalled);
    }

    /**
     * Tests if the propagation from the {@link CPSeqVar} is triggered correctly
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testPropagationSequence(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        AtomicBoolean propagateInsertCalled = new AtomicBoolean(false);
        AtomicBoolean propagateFixCalled = new AtomicBoolean(false);
        AtomicBoolean propagateExcludeCalled = new AtomicBoolean(false);
        AtomicBoolean propagateRequireCalled = new AtomicBoolean(false);

        CPConstraint cons = new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                seqVar.whenFixed(() -> propagateFixCalled.set(true));
                seqVar.whenInsert(() -> propagateInsertCalled.set(true));
                seqVar.whenExclude(() -> propagateExcludeCalled.set(true));
                seqVar.whenRequire(() -> propagateRequireCalled.set(true));
            }
        };

        cp.post(cons);
        seqVar.notBetween(start, 4, seqVar.memberAfter(start));
        cp.fixPoint();
        assertTrue(propagateExcludeCalled.get());
        assertFalse(propagateRequireCalled.get());
        assertFalse(propagateFixCalled.get());
        assertFalse(propagateInsertCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        seqVar.exclude(4); // already excluded, does nothing
        cp.fixPoint();
        assertFalse(propagateExcludeCalled.get());
        assertFalse(propagateRequireCalled.get());
        assertFalse(propagateFixCalled.get());
        assertFalse(propagateInsertCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        seqVar.exclude(3);
        cp.fixPoint();
        assertTrue(propagateExcludeCalled.get());
        assertFalse(propagateRequireCalled.get());
        assertFalse(propagateFixCalled.get());
        assertFalse(propagateInsertCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        seqVar.exclude(2);
        cp.fixPoint();
        assertTrue(propagateExcludeCalled.get());
        assertFalse(propagateRequireCalled.get());
        assertFalse(propagateFixCalled.get());
        assertFalse(propagateInsertCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        seqVar.insert(start, 8); // sequence: start -> 8 -> end
        cp.fixPoint();
        assertFalse(propagateExcludeCalled.get());
        assertFalse(propagateFixCalled.get());
        assertTrue(propagateInsertCalled.get());
        assertTrue(propagateRequireCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        seqVar.insert(8, 1); // sequence: start -> 8 -> 1 -> end
        cp.fixPoint();
        assertFalse(propagateExcludeCalled.get());
        assertFalse(propagateFixCalled.get());
        assertTrue(propagateInsertCalled.get());
        assertTrue(propagateRequireCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        seqVar.require(0);
        cp.fixPoint();
        assertFalse(propagateExcludeCalled.get());
        assertFalse(propagateFixCalled.get());
        assertFalse(propagateInsertCalled.get());
        assertTrue(propagateRequireCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        seqVar.require(9);
        cp.fixPoint();
        assertFalse(propagateExcludeCalled.get());
        assertFalse(propagateFixCalled.get());
        assertFalse(propagateInsertCalled.get());
        assertTrue(propagateRequireCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        seqVar.exclude(4);
        seqVar.exclude(5);
        seqVar.exclude(7);
        cp.fixPoint();
        assertTrue(propagateExcludeCalled.get());
        assertFalse(propagateFixCalled.get());
        assertFalse(propagateInsertCalled.get());
        assertFalse(propagateRequireCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        seqVar.insert(start, 0);
        seqVar.insert(1, 9); // sequence: start -> 0 -> 8 -> 1 -> 9 -> end
        cp.fixPoint();
        assertFalse(propagateExcludeCalled.get());
        assertFalse(propagateFixCalled.get());
        assertTrue(propagateInsertCalled.get());
        assertFalse(propagateRequireCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        // only node 6 is unassigned at the moment
        StateManager sm = cp.getStateManager();
        sm.saveState();
        seqVar.exclude(6);
        cp.fixPoint();
        assertTrue(propagateExcludeCalled.get());
        assertTrue(propagateFixCalled.get());  // no possible node remain
        assertFalse(propagateInsertCalled.get());
        assertFalse(propagateRequireCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        sm.restoreState();
        sm.saveState();

        seqVar.insert(0, 6); // sequence: start -> 0 -> 6 -> 8 -> 1 -> 9 -> end
        cp.fixPoint();
        assertFalse(propagateExcludeCalled.get());
        assertTrue(propagateFixCalled.get());  // no possible node remain
        assertTrue(propagateInsertCalled.get());
        assertTrue(propagateRequireCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        sm.restoreState();

        // require the node and force its insertion by removing all its insertions except one
        seqVar.require(6); // sequence: start -> 0 -> 8 -> 1 -> 9 -> end
        cp.fixPoint();
        assertFalse(propagateExcludeCalled.get());
        assertFalse(propagateFixCalled.get());
        assertFalse(propagateInsertCalled.get());
        assertTrue(propagateRequireCalled.get());
        resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);

        for (int node: new int[] {0, 1, 9}) {
            seqVar.notBetween(node, 6, seqVar.memberAfter(node));
            cp.fixPoint();
            assertFalse(propagateExcludeCalled.get());
            assertFalse(propagateFixCalled.get());
            assertFalse(propagateInsertCalled.get());
            assertFalse(propagateRequireCalled.get());
            resetPropagators(propagateInsertCalled, propagateFixCalled, propagateExcludeCalled, propagateRequireCalled);
        }
        // this removal means that only one point remains, forcing the insertion of the node

        seqVar.notBetween(start, 6, seqVar.memberAfter(start)); // sequence: start -> 0 -> 8 -> 6-> 1 -> 9 -> end
        cp.fixPoint();
        assertFalse(propagateExcludeCalled.get());
        assertTrue(propagateFixCalled.get());
        assertTrue(propagateInsertCalled.get());
        assertFalse(propagateRequireCalled.get());
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwInconsistencyDoubleInsert(CPSeqVar seqVar) {
        seqVar.insert(start, 4);
        seqVar.insert(start, 8); // sequence: start -> 8 -> 4 -> end
        assertThrowsExactly(IllegalArgumentException.class, () -> seqVar.insert(4, 8));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwNoInconsistencyDoubleInsert(CPSeqVar seqVar) {
        seqVar.insert(start, 8);
        seqVar.insert(start, 8); // TODO is it expected? double insertions at the same point are valid
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwNoInconsistencyDoubleExclude(CPSeqVar seqVar) {
        seqVar.exclude(8);
        seqVar.exclude(8);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwNoInconsistencyDoubleRequire(CPSeqVar seqVar) {
        seqVar.require(8);
        seqVar.require(8);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwNoInconsistencyRequireInsert(CPSeqVar seqVar) {
        seqVar.require(8);
        seqVar.insert(start, 8);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwNoInconsistencyInsertRequire(CPSeqVar seqVar) {
        seqVar.insert(start, 8);
        seqVar.require(8);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwInconsistencyExcludeInsert(CPSeqVar seqVar) {
        seqVar.exclude(8);
        assertThrowsExactly(InconsistencyException.class, () -> seqVar.insert(start, 8));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwInconsistencyInsertExclude(CPSeqVar seqVar) {
        seqVar.insert(start, 8);
        assertThrowsExactly(InconsistencyException.class, () -> seqVar.exclude(8));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwInconsistencyInvalidInsert1(CPSeqVar seqVar) {
        assertThrowsExactly(InconsistencyException.class, () -> seqVar.insert(2, 8),
                "An insertion happened after a node that was not member of the sequence");
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwInconsistencyInvalidInsert2(CPSeqVar seqVar) {
        assertThrowsExactly(InconsistencyException.class, () -> seqVar.insert(end, 8),
                "No node can be inserted after end");
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwInconsistencyInvalidInsert3(CPSeqVar seqVar) {
        seqVar.notBetween(start, 8, seqVar.memberAfter(start));
        assertThrowsExactly(InconsistencyException.class, () -> seqVar.insert(start, 8),
                "An insertion cannot happen if one of the edge used do not exist");
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwInconsistencyRequireExclude(CPSeqVar seqVar) {
        seqVar.require(8);
        assertThrowsExactly(InconsistencyException.class, () -> seqVar.exclude(8));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void throwInconsistencyExcludeRequire(CPSeqVar seqVar) {
        seqVar.exclude(8);
        assertThrowsExactly(InconsistencyException.class, () -> seqVar.require(8));
    }

    @Disabled // no longer valid per last changes
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void removeDirectSuccessor(CPSeqVar seqVar) {
        seqVar.insert(start, 1);
        seqVar.insert(1, 2);
        seqVar.insert(2, 3);
        // start -> 1 -> 2 -> 3 -> end
        record Edge(int from, int by, int to) {};
        for (Edge e: new Edge[] {new Edge(start, 1, 2), new Edge(1, 2, 3), new Edge(2, 3, end)}) {
            seqVar.getSolver().getStateManager().saveState();
            assertThrowsExactly(InconsistencyException.class, () -> seqVar.notBetween(e.from, e.by, e.to));
            seqVar.getSolver().getStateManager().restoreState();
        }
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void removeDirectSuccessorIgnored1(CPSeqVar seqVar) {
        assertDoesNotThrow(() -> seqVar.notBetween(start, end, seqVar.memberAfter(start)));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void removeDirectSuccessorIgnored2(CPSeqVar seqVar) {
        seqVar.insert(start, 1);
        seqVar.insert(1, 2);
        seqVar.insert(2, 3);
        // start -> 1 -> 2 -> 3 -> end
        record Edge(int from, int by, int to) {};
        for (Edge e: new Edge[] {new Edge(start, 1, 1), new Edge(1, 2, 2), new Edge(2, 3, 3)}) {
            seqVar.getSolver().getStateManager().saveState();
            assertDoesNotThrow(() -> seqVar.notBetween(e.from, e.by, e.to));
            seqVar.getSolver().getStateManager().restoreState();
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBranchingMaintainsState(CPSolver cp) {
        int nNodes = 7;
        CPSeqVar seqVar = CPFactory.makeSeqVar(cp, nNodes, nNodes-2, nNodes-1);
        DFSearch search = makeDfs(cp, firstFail(seqVar));
        search.onSolution(() -> {
            assertTrue(seqVar.isFixed());
            assertEquals(seqVar.nNode(MEMBER), seqVar.nNode(REQUIRED));
            int[] member = new int[seqVar.nNode(MEMBER)];
            int[] possible = new int[seqVar.nNode(POSSIBLE)];
            int[] excluded = new int[seqVar.nNode(EXCLUDED)];
            seqVar.fillNode(member, MEMBER_ORDERED);
            seqVar.fillNode(possible, POSSIBLE);
            seqVar.fillNode(excluded, EXCLUDED);
            assertSeqVar(seqVar, member, possible, excluded);
            String ordering = seqVar.membersOrdered("");
            assertEquals(member.length, ordering.length());
        });
        int nSolutions = 0; // number of sequences that can be constructed
        for (int n = 0; n <= nNodes - 2; n++) {
            // visit n nodes within the nNodes-2 possible (omitting the start and end)
            //  * arrangements for the nNodes
            nSolutions += nCk(nNodes-2, n) * fact(n);
        }
        SearchStatistics statistics = search.solve();
        assertEquals(nSolutions, statistics.numberOfSolutions());
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
        nNodes, seed
        10,     0
        20,     1
        20,     2
        20,     42
        42,     0
        42,     1
        42,     2
        42,     42
        """)
    public void testRandomOperationsOnSequenceMaintainInvariants(int nNodes, int seed) {
        CPSolver cp = makeSolver();
        CPSeqVar seqVar = CPFactory.makeSeqVar(cp, nNodes, nNodes-2, nNodes-1);
        assertCounterInvariant(seqVar);
        Random random = new Random(seed);
        int[] insertable = new int[nNodes];
        int[] neighbors = new int[nNodes];
        DFSearch search = makeDfs(cp, () -> {
            // invariant should always be respected when entering the search,
            // because they are tested after posting the constraints
            //assertCounterInvariant(seqVar);
            //System.out.println(seqVar);
            String before = seqVar.toGraphViz();
            if (seqVar.isFixed())
                return EMPTY;
            double prob = random.nextDouble();
            int nPossible = seqVar.nNode(POSSIBLE);
            double probRemoveEdge = 0.6;
            double probExclude = nPossible != 0 ? 0.2 : 0.0;
            double probInsert = 1 - probRemoveEdge - probExclude;
            if (prob < probRemoveEdge) { // try to remove an edge
                // choose randomly an edge to delete
                int nInsertable = seqVar.fillNode(insertable, INSERTABLE);
                int chosenNode = insertable[random.nextInt(nInsertable)];
                int nPred = seqVar.fillInsert(chosenNode, neighbors);
                int pred = neighbors[random.nextInt(nPred)];
                return new Runnable[] {() ->{
                    //assertCounterInvariant(seqVar);
                    cp.post(notBetween(seqVar, pred, chosenNode, seqVar.memberAfter(pred)));
                    try {
                        assertCounterInvariant(seqVar);
                    } catch (AssertionFailedError e) {
                        System.err.println("error with " + seqVar);
                        String after = seqVar.toGraphViz();
                        System.err.println("Sequence before: \n" + before);
                        System.err.println("Failed to remove edge (" + pred + ", " + chosenNode + ")");
                        System.err.println("Sequence after: \n" + after);
                        throw e;
                    }}
                        , () -> {}};
            } else if (prob < probRemoveEdge + probExclude) { // try to exclude a node
                // choose randomly a node to remove
                seqVar.fillNode(insertable, POSSIBLE);
                int chosenNode = insertable[random.nextInt(nPossible)];
                return new Runnable[] {() -> {
                    cp.post(exclude(seqVar, chosenNode));
                    try {
                        assertCounterInvariant(seqVar);
                    } catch (AssertionFailedError e) {
                        System.err.println("error with " + seqVar);
                        String after = seqVar.toGraphViz();
                        System.err.println("Sequence before: \n" + before);
                        System.err.println("Failed to exclude " + chosenNode);
                        System.err.println("Sequence after: \n" + after);
                        throw e;
                    }}
                        , () -> {}};
            } else { // try to insert a node
                // choose randomly a node to insert
                int nPending = seqVar.fillNode(insertable, INSERTABLE);
                int nInsertable = 0;
                for (int i = 0 ; i < nPending ; i++) {
                    int node = insertable[i];
                    if (seqVar.nInsert(node) > 0) { // ensure that the insertable node has at least one insertion at the current time
                        neighbors[nInsertable] = node;
                        nInsertable++;
                    }
                }
                int chosenNode = neighbors[random.nextInt(nInsertable)];
                // choose a random insertion point for it
                int nPred = seqVar.fillInsert(chosenNode, neighbors);
                int pred = neighbors[random.nextInt(nPred)];
                return new Runnable[] {() -> {
                    cp.post(insert(seqVar, pred, chosenNode));
                    try {
                        assertCounterInvariant(seqVar);
                    } catch (AssertionFailedError | IllegalArgumentException e) {
                        System.err.println("error with " + seqVar);
                        String after = seqVar.toGraphViz();
                        System.err.println("Sequence before: \n" + before);
                        System.err.println("Failed to insert " + pred + " -> " + chosenNode);
                        System.err.println("Sequence after: \n" + after);
                        throw e;
                    }
                }, () -> {}};
            }
        });
        search.solve(searchStatistics -> searchStatistics.numberOfNodes() > 5000);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMaintainCounter(CPSolver cp) {
        CPSeqVar seqVar = makeSeqVar(cp, 5, 0, 3);
        int[] inserts = new int[3];
        seqVar.insert(0, 1); // 0 -> 1 -> 3
        assertEquals(2, seqVar.nInsert(4));
        assertEquals(2, seqVar.fillInsert(4, inserts));
        seqVar.insert(1, 2); // 0 -> 1 -> 2 -> 3

        assertEquals(3, seqVar.nInsert(4));
        assertEquals(3, seqVar.fillInsert(4, inserts));
        Arrays.sort(inserts);
        assertArrayEquals(new int[] {0, 1, 2}, inserts);

        seqVar.notBetween(1, 4, seqVar.memberAfter(1));

        assertEquals(2, seqVar.nInsert(4));
        assertEquals(2, seqVar.fillInsert(4, inserts));
        assertArrayEquals(new int[] {0, 2}, Arrays.stream(inserts).limit(2).sorted().toArray());

        seqVar.notBetween(2, 4, seqVar.memberAfter(2));

        assertEquals(1, seqVar.nInsert(4));
        assertEquals(1, seqVar.fillInsert(4, inserts));
        assertArrayEquals(new int[] {0}, Arrays.stream(inserts).limit(1).sorted().toArray());

    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testRequireFromView(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        StateManager sm = cp.getStateManager();
        CPBoolVar isNode0Required = seqVar.getNodeVar(0).isRequired();
        CPBoolVar isNode1Required = seqVar.getNodeVar(1).isRequired();
        CPBoolVar isNode2Required = seqVar.getNodeVar(2).isRequired();

        for (Runnable[] ops: new Runnable[][] {
                // always 3 same actions, expressed differently: require node 0, exclude node 1, require node 2
                {() -> isNode0Required.fix(true), () -> isNode1Required.fix(false), () -> isNode2Required.fix(true)},
                {() -> isNode0Required.fix(1), () -> isNode1Required.fix(0), () -> isNode2Required.fix(1)},
                {() -> isNode0Required.remove(0), () -> isNode1Required.remove(1), () -> isNode2Required.remove(0)},
                {() -> isNode0Required.removeBelow(1), () -> isNode1Required.removeAbove(0), () -> isNode2Required.removeBelow(1)},
        }) {
            sm.saveState();
            ops[0].run(); // require node 0
            // this will force the insertion of the node
            assertTrue(seqVar.isNode(0, REQUIRED));
            assertTrue(seqVar.isNode(0, MEMBER));
            assertTrue(isNode0Required.isTrue());
            assertFalse(isNode0Required.isFalse());
            int[] member1 = new int[]{start, 0, end};
            int[] possible1 = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
            int[] excluded1 = new int[]{};
            assertSeqVar(seqVar, member1, possible1, excluded1);

            ops[1].run(); // exclude node 1
            // this will force the exclusion of the node
            assertTrue(seqVar.isNode(1, EXCLUDED));
            assertFalse(isNode1Required.isTrue());
            assertTrue(isNode1Required.isFalse());
            int[] member2 = new int[]{start, 0, end};
            int[] possible2 = new int[]{2, 3, 4, 5, 6, 7, 8, 9};
            int[] excluded2 = new int[]{1};
            assertSeqVar(seqVar, member2, possible2, excluded2);

            ops[2].run(); // require node 2
            // this cannot force the insertion of the node: it has 2 places valid for insertions
            assertTrue(seqVar.isNode(2, REQUIRED));
            assertFalse(seqVar.isNode(2, MEMBER));
            assertTrue(isNode2Required.isTrue());
            assertFalse(isNode2Required.isFalse());
            int[] member3 = new int[]{start, 0, end};
            int[] required3 = new int[]{start, 0, end, 2};
            int[] possible3 = new int[]{3, 4, 5, 6, 7, 8, 9};
            int[] excluded3 = new int[]{1};
            assertSeqVar(seqVar, member3, required3, possible3, excluded3);
            sm.restoreState();
        }
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testPropagationCalledOnViews(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        CPBoolVar isNode0Required = seqVar.getNodeVar(0).isRequired(); // required

        AtomicBoolean propagateBoundChangeCalled = new AtomicBoolean(false);
        AtomicBoolean propagateFixCalled = new AtomicBoolean(false);
        AtomicBoolean propagateDomainChangeCalled = new AtomicBoolean(false);

        CPConstraint cons = new AbstractCPConstraint(cp) {
            @Override
            public void post() {
                isNode0Required.whenFixed(() -> propagateFixCalled.set(true));
                isNode0Required.whenDomainChange(() -> propagateDomainChangeCalled.set(true));
                isNode0Required.whenBoundChange(() -> propagateBoundChangeCalled.set(true));
            }
        };

        cp.post(cons);
        int i = 0;

        for (Runnable[] ops: new Runnable[][] {
                // always 2 same actions, expressed differently: require node 0, exclude node 0
                {() -> isNode0Required.fix(true), () -> isNode0Required.fix(false)},
                {() -> isNode0Required.fix(1), () -> isNode0Required.fix(0)},
                {() -> isNode0Required.removeBelow(1), () -> isNode0Required.removeAbove(0)},
                {() -> isNode0Required.remove(0), () -> isNode0Required.remove(1)},
        }) {
            cp.getStateManager().saveState();

            cp.getStateManager().saveState();

            ops[0].run(); // require node 0
            cp.fixPoint();

            assertTrue(propagateBoundChangeCalled.get());
            assertTrue(propagateDomainChangeCalled.get());
            assertTrue(propagateFixCalled.get());
            resetPropagators(propagateBoundChangeCalled, propagateFixCalled, propagateDomainChangeCalled);

            ops[0].run(); // require node 0, which is already done. this does nothing and should not trigger the constraints
            cp.fixPoint();

            assertFalse(propagateBoundChangeCalled.get());
            assertFalse(propagateDomainChangeCalled.get());
            assertFalse(propagateFixCalled.get());
            resetPropagators(propagateBoundChangeCalled, propagateFixCalled, propagateDomainChangeCalled);

            cp.getStateManager().restoreState();

            ops[1].run(); // exclude node 0
            cp.fixPoint();

            assertTrue(propagateBoundChangeCalled.get());
            assertTrue(propagateDomainChangeCalled.get());
            assertTrue(propagateFixCalled.get());
            resetPropagators(propagateBoundChangeCalled, propagateFixCalled, propagateDomainChangeCalled);

            ops[1].run(); // exclude node 0, which is already done. this does nothing and should not trigger the propagation
            cp.fixPoint();

            assertFalse(propagateBoundChangeCalled.get());
            assertFalse(propagateDomainChangeCalled.get());
            assertFalse(propagateFixCalled.get());
            resetPropagators(propagateBoundChangeCalled, propagateFixCalled, propagateDomainChangeCalled);

            cp.getStateManager().restoreState();
        }

    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFillOnViews(CPSeqVar seqVar) {
        seqVar.insert(start, 0); // start -> 0 -> end
        seqVar.require(1);
        seqVar.exclude(2);

        int[] oneEntryActual = new int[1];
        int[] twoEntriesActual = new int[2];

        CPBoolVar isNode0Required = seqVar.getNodeVar(0).isRequired(); // required
        CPBoolVar isNode1Required = seqVar.getNodeVar(1).isRequired(); // required
        CPBoolVar isNode2Required = seqVar.getNodeVar(2).isRequired(); // excluded
        CPBoolVar isNode3Required = seqVar.getNodeVar(3).isRequired(); // possible
        CPBoolVar isStartRequired = seqVar.getNodeVar(start).isRequired(); // required
        CPBoolVar isEndRequired = seqVar.getNodeVar(end).isRequired(); // required

        // test operations over required nodes
        for (CPBoolVar requiredNode: new CPBoolVar[] {isNode0Required, isNode1Required, isStartRequired, isEndRequired}) {
            assertTrue(requiredNode.isTrue());
            assertFalse(requiredNode.isFalse());
            assertTrue(requiredNode.isFixed());
            assertEquals(1, requiredNode.size());
            assertEquals(1, requiredNode.min());
            assertEquals(1, requiredNode.max());
            for (int i : new int[] {-2, -1, 0, 2, 3}) {
                assertFalse(requiredNode.contains(i));
            }
            assertTrue(requiredNode.contains(1));
            assertEquals(1, requiredNode.fillArray(oneEntryActual));
            assertEquals(1, oneEntryActual[0]);
        }

        // test operations over excluded nodes
        assertFalse(isNode2Required.isTrue());
        assertTrue(isNode2Required.isFalse());
        assertTrue(isNode2Required.isFixed());
        assertEquals(1, isNode2Required.size());
        assertEquals(0, isNode2Required.min());
        assertEquals(0, isNode2Required.max());
        for (int i : new int[] {-2, -1, 1, 2, 3}) {
            assertFalse(isNode2Required.contains(i));
        }
        assertTrue(isNode2Required.contains(0));
        assertEquals(1, isNode2Required.fillArray(oneEntryActual));
        assertEquals(0, oneEntryActual[0]);

        // test operations over possible nodes
        assertFalse(isNode3Required.isTrue());
        assertFalse(isNode3Required.isFalse());
        assertFalse(isNode3Required.isFixed());
        assertEquals(2, isNode3Required.size());
        assertEquals(0, isNode3Required.min());
        assertEquals(1, isNode3Required.max());
        for (int i : new int[] {-2, -1, 2, 3}) {
            assertFalse(isNode3Required.contains(i));
        }
        assertTrue(isNode3Required.contains(0));
        assertTrue(isNode3Required.contains(1));
        assertEquals(2, isNode3Required.fillArray(twoEntriesActual));
        Arrays.sort(twoEntriesActual);
        assertEquals(0, twoEntriesActual[0]);
        assertEquals(1, twoEntriesActual[1]);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testEmptyViewThrowsInconsistency1(CPSeqVar seqVar) {
        CPBoolVar isNode0Required = seqVar.getNodeVar(0).isRequired();
        assertThrowsExactly(InconsistencyException.class, () -> isNode0Required.removeBelow(2));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testEmptyViewThrowsInconsistency2(CPSeqVar seqVar) {
        CPBoolVar isNode0Required = seqVar.getNodeVar(0).isRequired();
        assertThrowsExactly(InconsistencyException.class, () -> isNode0Required.removeAbove(-1));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testEmptyViewThrowsInconsistency3(CPSeqVar seqVar) {
        CPBoolVar isNode0Required = seqVar.getNodeVar(0).isRequired();
        assertThrowsExactly(InconsistencyException.class, () -> isNode0Required.fix(2));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testEmptyViewThrowsInconsistency4(CPSeqVar seqVar) {
        CPBoolVar isNode0Required = seqVar.getNodeVar(0).isRequired();
        assertThrowsExactly(InconsistencyException.class, () -> isNode0Required.fix(-1));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testNotBetweenForExcludingRequiredNode(CPSeqVar seqVar) {
        seqVar.insert(seqVar.start(), 1);
        seqVar.require(2);
        assertThrowsExactly(InconsistencyException.class, () -> seqVar.notBetween(seqVar.start(), 2, seqVar.end()));
    }


    private static int fact(int i) {
        if (i <= 1)
            return 1;
        return i * fact(i - 1);
    }

    /**
     * Number of ways to choose k elements in a set of n elements
     * @param n number of items in the set
     * @param k number of items to pick
     * @return Number of ways to choose k elements in a set of n elements
     */
    private static int nCk(int n, int k) {
        return fact(n) / (fact(k) * fact(n-k));
    }
}
