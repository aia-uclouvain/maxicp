/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.seqvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.firstFailBinary;

public class CumulativeTest extends CPSolverTest {

    static int nNodes = 10;
    static int start = 8;
    static int end = 9;

    public static Stream<CPSeqVar> getSeqVar() {
        return getSolver().map(cp -> CPFactory.makeSeqVar(cp, nNodes, start, end));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testInitCumulative2(CPSeqVar seqVar) {
        int[] capacity = new int[] {1, 1};
        assertDoesNotThrow(() -> seqVar.getSolver()
                .post(new Cumulative(seqVar, new int[] {0, 1}, new int[] {2, 3}, capacity, 2)));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testOverLoad1(CPSeqVar seqVar) {
        int[] capacity = new int[] {2, 2, 1, 1};
        int[] p = new int[] {0, 1, 2, 3};
        int[] d = new int[] {4, 5, 6, 7};
        seqVar.insert(seqVar.start(), p[0]);
        seqVar.insert(p[0], p[1]);
        seqVar.insert(p[1], d[0]);
        // seqVar at this point: start -> p0   -> p1   -> d0   -> end
        // capacity:                0, 0    0, 2    2, 4    2, 0    0, 0
        assertThrowsExactly(InconsistencyException.class, () -> seqVar.getSolver()
                .post(new Cumulative(seqVar, new int[]{0, 1, 2, 3}, new int[]{4, 5, 6, 7}, capacity, 3)));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testOverLoad2(CPSeqVar seqVar) {
        int[] capacity = new int[] {2, 2, 1, 1};
        int[] p = new int[] {0, 1, 2, 3};
        int[] d = new int[] {4, 5, 6, 7};
        seqVar.insert(seqVar.start(), p[0]);
        seqVar.insert(p[0], d[1]);
        seqVar.insert(d[1], d[0]);
        // seqVar at this point: start -> p0   -> d1   -> d0   -> end
        // capacity:                0, 0    0, 2    4, 2    2, 0    0, 0
        assertThrowsExactly(InconsistencyException.class, () -> seqVar.getSolver()
                .post(new Cumulative(seqVar, new int[]{0, 1, 2, 3}, new int[]{4, 5, 6, 7}, capacity, 3)));
    }

    // this test assess both the filtering of partially inserted activities and non-inserted activities
    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoInsertForStart(CPSolver cp) {
        CPSeqVar seqVar = makeSeqVar(cp, 12, 10, 11);
        int[] capacity = new int[] {2, 2, 1, 2, 3};
        int[] p = new int[] {0, 1, 2, 3, 4};
        int[] d = new int[] {5, 6, 7, 8, 9};
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        cp.post(insert(seqVar, p[0], p[2]));
        cp.post(insert(seqVar, p[2], d[2]));
        cp.post(insert(seqVar, d[2], d[1]));
        cp.post(new Cumulative(seqVar, p, d, capacity, 3));
        // seqVar at this point: start -> p0  -> p2  -> d2  -> d1  -> end
        // load:                   0, 0   0, 2   0, 1   1, 0   2, 0   0, 0
        assertEquals(6, seqVar.nNode(MEMBER));
        assertFalse(seqVar.hasInsert(seqVar.start(), p[1]));
        assertTrue(seqVar.hasInsert(p[0], p[1])); // start -> p0 -> p1 -> p2 -> d2 -> d1 -> end feasible
        assertTrue(seqVar.hasInsert(p[2], p[1]));
        assertTrue(seqVar.hasInsert(d[2], p[1]));
        assertFalse(seqVar.hasInsert(d[1], p[1]));

        assertFalse(seqVar.hasInsert(seqVar.start(), d[0]));
        assertTrue(seqVar.hasInsert(p[0], p[1]));
        assertTrue(seqVar.hasInsert(p[2], p[3]));
        assertTrue(seqVar.hasInsert(d[2], d[3]));
        assertFalse(seqVar.hasInsert(d[1], p[1]));

        // activity 3 has a capacity of 2
        for (int node: new int[] {p[3], d[3]}) {
            assertTrue(seqVar.hasInsert(seqVar.start(), node));
            assertTrue(seqVar.hasInsert(p[0], node));
            assertTrue(seqVar.hasInsert(p[2], node));
            assertTrue(seqVar.hasInsert(d[2], node));
            assertTrue(seqVar.hasInsert(d[1], node));
        }

        // activity 4 has a capacity of 3
        for (int node: new int[] {p[4], d[4]}) {
            assertTrue(seqVar.hasInsert(seqVar.start(), node));
            assertTrue(seqVar.hasInsert(p[0], node));
            assertFalse(seqVar.hasInsert(p[2], node));
            assertTrue(seqVar.hasInsert(d[2], node));
            assertTrue(seqVar.hasInsert(d[1], node));
        }

        // remove insertion (p0, d0). This should remove insertion for activity 3
        cp.post(notBetween(seqVar, p[0], d[0], seqVar.memberAfter(p[0])));
        // d0 can at the soonest be inserted after p2
        // this means that activity 3 cannot be inserted after p0 anymore
        for (int node: new int[] {p[3], d[3]}) {
            assertTrue(seqVar.hasInsert(seqVar.start(), node));
            assertFalse(seqVar.hasInsert(p[0], node));
            assertTrue(seqVar.hasInsert(p[2], node));
            assertTrue(seqVar.hasInsert(d[2], node));
            assertTrue(seqVar.hasInsert(d[1], node));
        }

        cp.post(insert(seqVar, p[2], d[0]));
        // start -> p0  -> p2  -> d0  -> d2  -> d1  -> end
        // load     0, 2   2, 3   3, 2   1, 0   2, 0
        for (int node: new int[] {p[3], d[3]}) {
            assertTrue(seqVar.hasInsert(seqVar.start(), node));
            assertFalse(seqVar.hasInsert(p[0], node));
            assertFalse(seqVar.hasInsert(p[2], node));
            assertTrue(seqVar.hasInsert(d[0], node));
            assertTrue(seqVar.hasInsert(d[2], node));
            assertTrue(seqVar.hasInsert(d[1], node));
        }
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testNonInserted1(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] p = new int[]{0, 1, 2, 3};
        int[] d = new int[]{4, 5, 6, 7};
        int[] capacity = new int[]{2, 2, 1, 1};
        cp.post(insert(seqVar, seqVar.start(), 2));
        cp.post(insert(seqVar, 2, 0));
        cp.post(insert(seqVar, 0, 4));
        cp.post(insert(seqVar, 4, 3));
        cp.post(insert(seqVar, 3, 7));
        cp.post(insert(seqVar, 7, 6));
        // seqVar: start -> p2  -> p0  -> d0  -> p3  -> d3  -> d2  -> end
        // load:            0, 1   1, 3   3, 1   1, 2   2, 1   1, 0
        cp.post(new Cumulative(seqVar, p, d, capacity, 3));

        for (int node: new int[] {p[1], d[1]}) {
            assertTrue(seqVar.hasInsert(seqVar.start(), node));
            assertTrue(seqVar.hasInsert(p[2], node));  // capacity: 1
            assertFalse(seqVar.hasInsert(p[0], node)); // capacity: 3
            assertTrue(seqVar.hasInsert(d[0], node));  // capacity: 1
            assertFalse(seqVar.hasInsert(p[3], node)); // capacity: 2
            assertTrue(seqVar.hasInsert(d[3], node));  // capacity: 1
            assertTrue(seqVar.hasInsert(d[2], node));  // capacity: 0
        }
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testPartiallyInserted2(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {2, 2, 1, 1};
        int[] p = new int[]{0, 1, 2, 3};
        int[] d = new int[]{4, 5, 6, 7};
        cp.post(insert(seqVar, seqVar.start(), p[2]));
        cp.post(insert(seqVar, p[2], d[2]));
        cp.post(insert(seqVar, d[2], d[1]));
        cp.post(insert(seqVar, d[1], d[0]));
        cp.post(insert(seqVar, d[0], p[3]));
        cp.post(insert(seqVar, p[3], d[3])); // seqVar: start -> 2 -> 6 -> 5 -> 4 -> 3 -> 7 -> end
        cp.post(new Cumulative(seqVar, p,d, capacity, 3));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testPartiallyInserted3(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] p = new int[]{0, 1, 2, 3};
        int[] d = new int[]{4, 5, 6, 7};
        int[] capacity = new int[] {2, 2, 1, 1};
        cp.post(insert(seqVar, seqVar.start(), 1));
        cp.post(insert(seqVar, 1, 2));
        cp.post(insert(seqVar, 2, 0));
        cp.post(insert(seqVar, 0, 4));
        cp.post(insert(seqVar, 4, 6));
        // seqVar: start -> p1 -> p2 -> p0 -> d0 -> d2 -> end
        cp.post(new Cumulative(seqVar, p, d, capacity, 3));

        int end = 5; // end for start == 1
        assertFalse(seqVar.hasInsert(seqVar.start(), end)); // cannot insert end before start
        assertTrue(seqVar.hasInsert(1, end));
        assertTrue(seqVar.hasInsert(2, end));
        assertFalse(seqVar.hasInsert(0, end)); // from this point, the end exceeds the max capacity
        assertFalse(seqVar.hasInsert(4, end));
        assertFalse(seqVar.hasInsert(6, end));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testSeveralPartiallyInserted(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {2, 2, 1, 1};
        int[] p = new int[] {0, 1, 2, 3};
        int[] d = new int[] {4, 5, 6, 7};
        cp.post(new Cumulative(seqVar, p, d, capacity, 3));
        cp.post(insert(seqVar, seqVar.start(), p[2]));
        cp.post(insert(seqVar, p[2], p[3]));
        cp.post(insert(seqVar, p[2], d[0]));
        cp.post(insert(seqVar, p[3], d[1]));
        cp.post(insert(seqVar, d[1], d[2])); // start -> p2 -> d0 -> p3 -> d1 -> d2 -> end
        cp.post(insert(seqVar, p[2], p[0])); // start -> p2 -> p0 -> d0 -> p3 -> d1 -> d2 -> end
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testForceInsertion1(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {1, 1, 1, 1};
        int[] p = new int[] {0, 1, 2, 3};
        int[] d = new int[] {4, 5, 6, 7};
        cp.post(new Cumulative(seqVar, p, d, capacity, 1));
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        // d0 insertion is forced by precedence
        // start -> p0 -> d0 -> end
        assertTrue(seqVar.isNode(d[0], MEMBER));
        assertEquals(p[0], seqVar.memberBefore(d[0]));
        assertEquals(seqVar.end(), seqVar.memberAfter(d[0]));

        cp.post(insert(seqVar, d[0], p[1]));
        // d1 insertion is forced by precedence
        // start -> p0 -> d0 -> p1 -> d1 -> end
        assertTrue(seqVar.isNode(d[1], MEMBER));
        assertEquals(p[1], seqVar.memberBefore(d[1]));
        assertEquals(seqVar.end(), seqVar.memberAfter(d[1]));

        seqVar.insert(d[0], p[2]);
        // start -> p0 -> d0 -> p2 -> p1 -> d1 -> end
        cp.fixPoint();
        // d2 insertion is forced because it is required and cannot be placed after p1: this would exceed the load
        // start -> p0 -> d0 -> p2 -> d2 -> p1 -> d1 -> end
        assertTrue(seqVar.isNode(d[2], MEMBER));
        assertEquals(p[2], seqVar.memberBefore(d[2]));
        assertEquals(p[1], seqVar.memberAfter(d[2]));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testForceInsertion2(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {1, 1, 1, 1};
        int[] p = new int[] {0, 1, 2, 3};
        int[] d = new int[] {4, 5, 6, 7};
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        cp.post(insert(seqVar, p[0], p[1]));
        cp.post(insert(seqVar, p[1], p[2]));
        // start -> p0 -> p1 -> p2 -> end

        cp.post(new Cumulative(seqVar, p, d, capacity, 1));
        assertEquals(8, seqVar.nNode(MEMBER));
        int[] actualOrdering = new int[8];
        seqVar.fillNode(actualOrdering, MEMBER_ORDERED);
        int[] expectedOrdering = new int[] {seqVar.start(), p[0], d[0], p[1], d[1], p[2], d[2], seqVar.end()};
        assertArrayEquals(expectedOrdering, actualOrdering);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testForceInsertion3(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {1, 1, 1, 1};
        int[] p = new int[] {0, 1, 2, 3};
        int[] d = new int[] {4, 5, 6, 7};
        cp.post(insert(seqVar, seqVar.start(), d[0]));
        cp.post(insert(seqVar, d[0], d[1]));
        cp.post(insert(seqVar, d[1], d[2]));
        // start -> d0 -> d1 -> d2 -> end

        cp.post(new Cumulative(seqVar, p, d, capacity, 1));
        assertEquals(8, seqVar.nNode(MEMBER));
        int[] actualOrdering = new int[8];
        seqVar.fillNode(actualOrdering, MEMBER_ORDERED);
        int[] expectedOrdering = new int[] {seqVar.start(), p[0], d[0], p[1], d[1], p[2], d[2], seqVar.end()};
        assertArrayEquals(expectedOrdering, actualOrdering);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testForceInsertion4(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {1, 1, 1, 1};
        int[] p = new int[] {0, 1, 2, 3};
        int[] d = new int[] {4, 5, 6, 7};
        cp.post(insert(seqVar, seqVar.start(), d[0]));
        cp.post(insert(seqVar, d[0], p[1]));
        cp.post(insert(seqVar, p[1], d[2]));
        // start -> d0 -> p1 -> d2 -> end

        cp.post(new Cumulative(seqVar, p, d, capacity, 1));
        assertEquals(8, seqVar.nNode(MEMBER));
        int[] actualOrdering = new int[8];
        seqVar.fillNode(actualOrdering, MEMBER_ORDERED);
        int[] expectedOrdering = new int[] {seqVar.start(), p[0], d[0], p[1], d[1], p[2], d[2], seqVar.end()};
        assertArrayEquals(expectedOrdering, actualOrdering);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testForceInsertion5(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {1, 1, 1, 1};
        int[] p = new int[] {0, 1, 2, 3};
        int[] d = new int[] {4, 5, 6, 7};
        cp.post(insert(seqVar, seqVar.start(), d[0]));
        cp.post(insert(seqVar, d[0], p[1]));
        cp.post(insert(seqVar, p[1], d[2]));
        cp.post(insert(seqVar, d[2], p[3]));
        // start -> d0 -> p1 -> d2 -> p3 -> end
        cp.post(new Cumulative(seqVar, p, d, capacity, 1));
        // start -> p0 -> d0 -> p1 -> d2 -> p2 -> d2 -> p3 -> d3 -> end

        assertEquals(10, seqVar.nNode(MEMBER));
        int[] actualOrdering = new int[10];
        seqVar.fillNode(actualOrdering, MEMBER_ORDERED);
        int[] expectedOrdering = new int[] {seqVar.start(), p[0], d[0], p[1], d[1], p[2], d[2], p[3], d[3], seqVar.end()};
        assertArrayEquals(expectedOrdering, actualOrdering);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFindAllSolutions1(CPSolver cp) {
        int[] capacity = new int[] {2, 2, 2};
        int[] p = new int[] {0, 1, 2};
        int[] d = new int[] {3, 4, 5};
        CPSeqVar seqVar = CPFactory.makeSeqVar(cp, 8, 6, 7);
        for (int i = 0; i < 6 ; ++i)
            seqVar.require(i);
        cp.post(new Cumulative(seqVar, p, d, capacity, 3));
        DFSearch search = makeDfs(cp, firstFailBinary(seqVar));
        SearchStatistics stats = search.solve();
        assertEquals(6, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFindAllSolutions2(CPSolver cp) {
        int[] capacity = new int[] {1, 1, 1};
        int[] p = new int[] {0, 1, 2};
        int[] d = new int[] {3, 4, 5};
        CPSeqVar seqVar = CPFactory.makeSeqVar(cp, 8, 6, 7);
        cp.post(new Cumulative(seqVar, p, d, capacity, 1));
        DFSearch search = makeDfs(cp, firstFailBinary(seqVar));
        SearchStatistics stats = search.solve();
        assertEquals(16, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFindAllSolutions3(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {2, 2, 1, 1};
        int[] p = new int[] {0, 1, 2, 3};
        int[] d = new int[] {4, 5, 6, 7};
        for (int i = 0; i < 8 ; ++i)
            seqVar.require(i);
        cp.post(new Cumulative(seqVar, p, d, capacity, 3));
        DFSearch search = makeDfs(cp, firstFailBinary(seqVar));
        int[] nodes = new int[seqVar.nNode()];
        search.onSolution(() -> {
            int n = seqVar.fillNode(nodes, MEMBER_ORDERED);
            int capa = 0;
            for (int i = 0 ; i < n ; ++i) {
                int node = nodes[i];
                if (node < 4) {
                    // start of activity
                    capa += 1;
                } else if (node < 8) {
                    // end of activity
                    capa -= 1;
                }
                assertTrue(capa >= 0);
                assertTrue(capa <= 2);
            }
        });
        SearchStatistics stats = search.solve();
        assertTrue(stats.numberOfSolutions() >= 1);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFilterNonInserted1(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {2, 2, 1, 1};
        int[] p = new int[] {0, 1, 2, 3};
        int[] d = new int[] {4, 5, 6, 7};
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        cp.post(insert(seqVar, p[0], p[2]));
        cp.post(insert(seqVar, p[2], d[2]));
        cp.post(insert(seqVar, d[2], d[0]));
        Cumulative cumulative = new Cumulative(seqVar, p, d, capacity, 3);
        cp.post(cumulative);
        // seqVar:   start -> (p0) -> (p2) -> 2 (d2) -> 4 (d0) -> end
        // seqVar: capacity:   2       3         2         0
        // not inserted: drop
        assertTrue(seqVar.hasInsert(seqVar.start(), d[1]));
        assertFalse(seqVar.hasInsert(p[0], d[1]));
        assertFalse(seqVar.hasInsert(p[2], d[1]));
        assertFalse(seqVar.hasInsert(d[2], d[1]));
        assertTrue(seqVar.hasInsert(d[0], d[1]));
        // not inserted: pickup
        assertTrue(seqVar.hasInsert(seqVar.start(), p[1]));
        assertFalse(seqVar.hasInsert(p[0], p[1]));
        assertFalse(seqVar.hasInsert(p[2], p[1]));
        assertFalse(seqVar.hasInsert(d[2], p[1]));
        assertTrue(seqVar.hasInsert(d[0], p[1]));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFilterNonInserted2(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {1, 1};
        int[] p = new int[] {0, 1};
        int[] d = new int[] {5, 6};
        cp.post(new Cumulative(seqVar, p, d, capacity, 1));
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        cp.post(insert(seqVar, p[0], d[0]));
        // start -> p0 -> d0 -> end
        assertTrue(seqVar.hasInsert(start, p[1]));
        assertFalse(seqVar.hasInsert(p[0], p[1]));
        assertTrue(seqVar.hasInsert(d[0], p[1]));

        assertTrue(seqVar.hasInsert(start, d[1]));
        assertFalse(seqVar.hasInsert(p[0], d[1]));
        assertTrue(seqVar.hasInsert(d[0], d[1]));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testFilterNonInserted3(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {2, 2, 1, 1};
        cp.post(insert(seqVar, seqVar.start(), 2));
        cp.post(insert(seqVar, 2, 0));
        cp.post(insert(seqVar, 0, 4));
        cp.post(insert(seqVar, 4, 6));
        cp.post(new Cumulative(seqVar, new int[] {0, 1, 2, 3}, new int[] {4, 5, 6, 7}, capacity, 3));
        // seqVar: start -> 2   -> 0   -> 4   -> 6   -> end
        //           0, 0   0, 1   1, 3   3, 1   1, 0
        int start = 1; // activity with a load of 2
        int end = 5;
        assertTrue(seqVar.hasInsert(seqVar.start(), start));
        assertTrue(seqVar.hasInsert(2, start));
        assertFalse(seqVar.hasInsert(0, start));
        assertTrue(seqVar.hasInsert(4, start));
        assertTrue(seqVar.hasInsert(6, start));

        assertTrue(seqVar.hasInsert(seqVar.start(), end));
        assertTrue(seqVar.hasInsert(2, end));
        assertFalse(seqVar.hasInsert(0, end));
        assertTrue(seqVar.hasInsert(4, end));
        assertTrue(seqVar.hasInsert(6, end));
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFilterNonInserted4(CPSolver cp) {
        CPSeqVar seqVar = makeSeqVar(cp, 12, 10, 11);
        int[] capacity = new int[] {1, 1, 1, 1, 1};
        int[] p = new int[] {0, 1, 2, 3, 4};
        int[] d = new int[] {5, 6, 7, 8, 9};
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        cp.post(insert(seqVar, p[0], p[1]));
        cp.post(insert(seqVar, p[1], d[2]));
        cp.post(insert(seqVar, d[2], d[0]));
        cp.post(insert(seqVar, d[0], p[3]));
        // start -> p0 -> p1 -> d2 -> d0 -> p3 -> end
        cp.post(new Cumulative(seqVar, p, d, capacity, 2));
        // start -> p0 -> p1 -> d1 -> p2 -> d2 -> d0 -> p3 -> d3 -> end
        //          1     2     1     2     1     0     1     0

        int[] nodes = new int[seqVar.nNode(MEMBER)];
        seqVar.fillNode(nodes, MEMBER_ORDERED);
        int[] expected = new int[] {seqVar.start(), p[0], p[1], d[1], p[2], d[2], d[0], p[3], d[3], seqVar.end()};
        assertArrayEquals(expected, nodes);

        for (int node: new int[] {p[4], d[4]}) {
            assertTrue(seqVar.hasInsert(seqVar.start(), node));
            assertTrue(seqVar.hasInsert(p[0], node));
            assertFalse(seqVar.hasInsert(p[1], node));
            assertTrue(seqVar.hasInsert(d[1], node));
            assertFalse(seqVar.hasInsert(p[2], node));
            assertTrue(seqVar.hasInsert(d[2], node));
            assertTrue(seqVar.hasInsert(d[0], node));
            assertTrue(seqVar.hasInsert(p[3], node));
            assertTrue(seqVar.hasInsert(d[3], node));
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFilterNonInserted5(CPSolver cp) {
        CPSeqVar seqVar = makeSeqVar(cp, 12, 10, 11);
        int[] capacity = new int[] {1, 1, 1, 1, 1};
        int[] p = new int[] {0, 1, 2, 3, 4};
        int[] d = new int[] {5, 6, 7, 8, 9};
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        cp.post(insert(seqVar, p[0], d[0]));
        cp.post(notBetween(seqVar, seqVar.start(), p[1], seqVar.memberAfter(seqVar.start())));
        cp.post(notBetween(seqVar, p[0], p[1], seqVar.memberAfter(p[0])));
        // start -> p0 -> d0 -> end
        // p1 can only be put after d0
        // d1 can therefore only be put there as well
        cp.post(new Cumulative(seqVar, p, d, capacity, 2));

        assertFalse(seqVar.hasInsert(seqVar.start(), d[1]));
        assertFalse(seqVar.hasInsert(p[0], d[1]));
        assertTrue(seqVar.hasInsert(d[0], d[1]));

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFilterNonInserted6(CPSolver cp) {
        CPSeqVar seqVar = makeSeqVar(cp, 12, 10, 11);
        int[] capacity = new int[] {1, 1, 1, 1, 1};
        int[] p = new int[] {0, 1, 2, 3, 4};
        int[] d = new int[] {5, 6, 7, 8, 9};
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        cp.post(insert(seqVar, p[0], d[0]));
        cp.post(insert(seqVar, d[0], p[1]));
        cp.post(insert(seqVar, p[1], p[2]));
        cp.post(insert(seqVar, p[2], d[1]));
        cp.post(insert(seqVar, d[1], d[3]));
        cp.post(insert(seqVar, d[3], d[2]));
        // start -> p0 -> d0 -> p1 -> p2 -> d1 -> d3 -> d2 -> end
        // cannot put p3 directly before d3
        seqVar.notBetween(d[1], p[3], d[3]);
        int nMemberBefore = seqVar.nNode(MEMBER);
        cp.post(new Cumulative(seqVar, p, d, capacity, 3));
        // no insertion should have occurred
        assertEquals(nMemberBefore, seqVar.nNode(MEMBER));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFilterNonInserted7(CPSolver cp) {
        CPSeqVar seqVar = makeSeqVar(cp, 12, 10, 11);
        int[] capacity = new int[] {1, 2, 1, 1, 1};
        int[] p = new int[] {0, 1, 2, 3, 4};
        int[] d = new int[] {5, 6, 7, 8, 9};
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        cp.post(insert(seqVar, p[0], d[0]));
        // start -> p0 -> d0 -> end
        // cannot put p3 directly before d3
        seqVar.notBetween(seqVar.start(), p[1], p[0]);
        seqVar.notBetween(d[0], p[1], seqVar.end());
        cp.post(new Cumulative(seqVar, p, d, capacity, 2));
        assertTrue(seqVar.isNode(p[1], EXCLUDED));
        assertTrue(seqVar.isNode(d[1], EXCLUDED));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoFilterNonInserted(CPSolver cp) {
        CPSeqVar seqVar = makeSeqVar(cp, 12, 10, 11);
        int[] capacity = new int[] {1, 1, 2, 1, 1};
        int[] p = new int[] {0, 1, 2, 3, 4};
        int[] d = new int[] {5, 6, 7, 8, 9};
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        cp.post(insert(seqVar, p[0], d[1]));
        // start -> p0 -> d1 -> end
        // cannot put p3 directly before d3
        seqVar.notBetween(seqVar.start(), p[1], p[0]);
        seqVar.notBetween(d[1], p[1], seqVar.end());
        cp.post(new Cumulative(seqVar, p, d, capacity, 2));
        assertFalse(seqVar.isNode(p[2], EXCLUDED));
        assertFalse(seqVar.isNode(d[2], EXCLUDED));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoFilterNonInserted1(CPSolver cp) {
        CPSeqVar seqVar = makeSeqVar(cp, 12, 10, 11);
        int[] capacity = new int[] {1, 1, 1, 1, 1};
        int[] p = new int[] {0, 1, 2, 3, 4};
        int[] d = new int[] {5, 6, 7, 8, 9};
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        cp.post(insert(seqVar, p[0], d[0]));
        cp.post(insert(seqVar, d[0], p[1]));
        cp.post(insert(seqVar, p[1], d[1]));
        cp.post(insert(seqVar, d[1], p[2]));
        cp.post(insert(seqVar, p[2], d[2]));
        cp.post(insert(seqVar, d[2], p[3]));
        cp.post(insert(seqVar, p[3], d[3]));
        cp.post(new Cumulative(seqVar, p, d, capacity, 2));
        // start -> p0 -> d0 -> p1 -> d1 -> p2 -> d2 -> p3 -> d3 -> end
        int[] nodes = new int[seqVar.nNode(MEMBER)];
        seqVar.fillNode(nodes, MEMBER_ORDERED);
        int[] expected = new int[] {seqVar.start(), p[0], d[0], p[1], d[1], p[2], d[2], p[3], d[3], seqVar.end()};
        assertArrayEquals(expected, nodes);
        for (int pred: nodes) {
            if (pred != seqVar.end()) {
                for (int node : new int[]{p[4], d[4]}) {
                    assertTrue(seqVar.hasInsert(pred, node));
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoFilterNonInserted2(CPSolver cp) {
        CPSeqVar seqVar = makeSeqVar(cp, 12, 10, 11);
        int[] capacity = new int[] {1, 1, 1, 2, 1};
        int[] p = new int[] {0, 1, 2, 3, 4};
        int[] d = new int[] {5, 6, 7, 8, 9};
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        cp.post(insert(seqVar, p[0], d[0]));
        cp.post(insert(seqVar, d[0], p[1]));
        cp.post(insert(seqVar, p[1], d[2]));
        cp.post(new Cumulative(seqVar, p, d, capacity, 2));
        // start -> p0 -> d0 -> p1 -> d2 -> end
        int[] nodes = new int[seqVar.nNode(MEMBER)];
        seqVar.fillNode(nodes, MEMBER_ORDERED);
        int[] expected = new int[] {seqVar.start(), p[0], d[0], p[1], d[2], seqVar.end()};
        assertArrayEquals(expected, nodes);
        for (int node : new int[]{p[3], d[3]}) {
            assertTrue(seqVar.hasInsert(seqVar.start(), node));
            assertFalse(seqVar.hasInsert(p[0], node));
            assertTrue(seqVar.hasInsert(d[0], node));
            assertTrue(seqVar.hasInsert(p[1], node));
            assertTrue(seqVar.hasInsert(d[2], node));
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testComplexSequence(CPSolver cp) {
        CPSeqVar seqVar = makeSeqVar(cp, 14, 12, 13);
        int[] capacity = new int[] {1, 1, 1, 1, 1, 1};
        int[] p = new int[] {0, 1, 2, 3, 4, 5};
        int[] d = new int[] {6, 7, 8, 9, 10, 11};
        cp.post(insert(seqVar, seqVar.start(), p[0]));
        cp.post(insert(seqVar, p[0], d[1]));
        cp.post(insert(seqVar, d[1], d[2]));
        cp.post(insert(seqVar, d[2], p[3]));
        cp.post(insert(seqVar, p[3], d[0]));
        cp.post(insert(seqVar, d[0], d[3]));
        cp.post(insert(seqVar, d[3], p[4]));
        // start -> p0 -> d1 -> d2 -> p3 -> d0 -> d3 -> p4 -> end
        cp.post(new Cumulative(seqVar, p, d, capacity, 2));

        assertFalse(seqVar.hasEdge(seqVar.start(), p[2]));
        assertFalse(seqVar.hasEdge(p[0], p[2]));
        assertTrue(seqVar.hasEdge(d[1], p[2]));
        Set<Integer> invalidFrom = Set.of(p[3]);
        Set<Integer> invalidTo = Set.of(d[0]);
        int[] nonInsertedActivity = new int[] {p[5], d[5]};
        int node = seqVar.start();
        for (int i = 0 ; i < seqVar.nNode(MEMBER) ; i++) {
            for (int activityNode: nonInsertedActivity) {
                if (node == seqVar.start()) {
                    assertTrue(seqVar.hasEdge(node, activityNode));
                } else if (node == seqVar.end()) {
                    assertTrue(seqVar.hasEdge(activityNode, node));
                } else {
                    if (invalidFrom.contains(node)) {
                        assertFalse(seqVar.hasEdge(node, activityNode));
                    } else {
                        assertTrue(seqVar.hasEdge(node, activityNode));
                    }
                    if (invalidTo.contains(node)) {
                        assertFalse(seqVar.hasEdge(activityNode, node));
                    } else {
                        assertTrue(seqVar.hasEdge(activityNode, node));
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testExcludeActivity(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {2, 2, 1, 1};
        cp.post(insert(seqVar, seqVar.start(), 2));
        cp.post(insert(seqVar, 2, 0));
        cp.post(insert(seqVar, 0, 4));
        cp.post(insert(seqVar, 4, 6));
        int[] p = new int[] {0, 1, 2, 3};
        int[] d = new int[] {4, 5, 6, 7};
        cp.post(new Cumulative(seqVar, p, d, capacity, 3));
        assertTrue(seqVar.isNode(p[3], POSSIBLE));
        assertTrue(seqVar.isNode(d[3], POSSIBLE));
        // exclude the start of an activity
        // this should exclude the end of the activity as well
        cp.post(exclude(seqVar, p[3]));
        assertTrue(seqVar.isNode(p[3], EXCLUDED));
        assertTrue(seqVar.isNode(d[3], EXCLUDED));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testNodeInTwoActivities1(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {1, 1, 1};
        // start -> 0 -> 1 -> 2 -> end
        // node 0 is both the end of activity 0 and the start of 1
        cp.post(insert(seqVar, seqVar.start(), 0));
        cp.post(insert(seqVar, 0, 1));
        cp.post(insert(seqVar, 1, 2));
        int[] p = new int[] {0, 1, 3};
        int[] d = new int[] {1, 2, 4};
        assertDoesNotThrow(() -> cp.post(new Cumulative(seqVar, p, d, capacity, 1)));
        assertTrue(seqVar.isNode(p[2], POSSIBLE));
        assertTrue(seqVar.isNode(d[2], POSSIBLE));
        for (int node: new int[] {p[2], d[2]}) {
            assertTrue(seqVar.hasInsert(start, node));
            assertFalse(seqVar.hasInsert(0, node));
            assertFalse(seqVar.hasInsert(1, node));
            assertTrue(seqVar.hasInsert(2, node));
        }
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testNodeInTwoActivities2(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {1, 2, 1};
        // start -> 0 -> 1 -> 2 -> end
        // node 0 is both the start of activity 0 and the start of 1
        cp.post(insert(seqVar, seqVar.start(), 0));
        cp.post(insert(seqVar, 0, 1));
        cp.post(insert(seqVar, 1, 2));
        int[] p = new int[] {0, 0, 3};
        int[] d = new int[] {1, 2, 4};
        assertDoesNotThrow(() -> cp.post(new Cumulative(seqVar, p, d, capacity, 3)));
        assertTrue(seqVar.isNode(p[2], POSSIBLE));
        assertTrue(seqVar.isNode(d[2], POSSIBLE));
        for (int node: new int[] {p[2], d[2]}) {
            assertTrue(seqVar.hasInsert(start, node));
            assertFalse(seqVar.hasInsert(0, node));
            assertTrue(seqVar.hasInsert(1, node));
            assertTrue(seqVar.hasInsert(2, node));
        }
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testNodeInTwoActivities3(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {1, 2, 1};
        // start -> 0 -> 1 -> 2 -> end
        // node 2 is both the end of activity 0 and the end of 1
        cp.post(insert(seqVar, seqVar.start(), 0));
        cp.post(insert(seqVar, 0, 1));
        cp.post(insert(seqVar, 1, 2));
        int[] p = new int[] {0, 1, 3};
        int[] d = new int[] {2, 2, 4};
        assertDoesNotThrow(() -> cp.post(new Cumulative(seqVar, p, d, capacity, 3)));
        assertTrue(seqVar.isNode(p[2], POSSIBLE));
        assertTrue(seqVar.isNode(d[2], POSSIBLE));
        for (int node: new int[] {p[2], d[2]}) {
            assertTrue(seqVar.hasInsert(start, node));
            assertTrue(seqVar.hasInsert(0, node));
            assertFalse(seqVar.hasInsert(1, node));
            assertTrue(seqVar.hasInsert(2, node));
        }
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testStartAndEndAsActivities(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {1, 2, 1};
        int[] p = new int[] {start, 0, 2};
        int[] d = new int[] {end, 1, 3};
        assertDoesNotThrow(() -> cp.post(new Cumulative(seqVar, p, d, capacity, 2)));
        assertTrue(seqVar.isNode(p[1], EXCLUDED));
        assertTrue(seqVar.isNode(d[1], EXCLUDED));
        assertTrue(seqVar.isNode(p[2], POSSIBLE));
        assertTrue(seqVar.isNode(d[2], POSSIBLE));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testNodeAsBothStartAndEndOfSameActivity(CPSeqVar seqVar) {
        CPSolver cp = seqVar.getSolver();
        int[] capacity = new int[] {1, 1};
        int[] p = new int[] {0, 2};
        int[] d = new int[] {1, 2};
        assertThrowsExactly(IllegalArgumentException.class, () -> cp.post(new Cumulative(seqVar, p, d, capacity, 2)),
                "A node cannot be both the start and the end of the same activity");
    }

}
