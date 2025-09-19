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

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.exclude;
import static org.maxicp.cp.CPFactory.makeDfs;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;
import static org.maxicp.search.Searches.firstFail;

public class SubSequenceTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInit1(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        assertDoesNotThrow(() -> cp.post(new SubSequence(main, sub)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInit2(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        main.insert(0, 1);
        main.insert(1, 2);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        sub.insert(0, 1);
        sub.insert(1, 2);
        // main: 0 -> 1 -> 2 -> 8
        // sub:  0 -> 1 -> 2 -> 8
        assertDoesNotThrow(() -> cp.post(new SubSequence(main, sub)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInvalidInit(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        sub.insert(0, 1);
        main.exclude(1);
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new SubSequence(main, sub)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAddToMain1(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        sub.insert(0, 1);
        cp.post(new SubSequence(main, sub));
        assertTrue(main.isNode(1, MEMBER));
        assertEquals(0, main.memberBefore(1));
        assertEquals(8, main.memberAfter(1));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAddToMain2(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        sub.insert(0, 1);
        sub.insert(1, 2);
        sub.insert(2, 3);
        main.insert(0, 1);
        main.insert(1, 3);
        // sub : 0 -> 1 -> 2 -> 3 -> 8
        // main: 0 -> 1   ->    3 -> 8
        // 2 should be inserted between 1 and 3, as there is only one insertion valid for it
        cp.post(new SubSequence(main, sub));
        assertTrue(main.isNode(2, MEMBER));
        assertEquals(1, main.memberBefore(2));
        assertEquals(3, main.memberAfter(2));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAddToMain3(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        sub.insert(0, 1);
        sub.insert(1, 2);
        sub.insert(2, 3);
        main.insert(0, 1);
        main.insert(1, 3);
        // sub : 0 -> 1 -> 2 -> 3 -> 8
        // main: 0 -> 8
        // 1, 2, 3 should be inserted directly within the main sequence
        cp.post(new SubSequence(main, sub));
        for (int i = 0 ; i < 3 ; i++) {
            assertTrue(main.isNode(i, MEMBER));
            assertEquals(i, main.memberBefore(i+1));
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMultipleNodesInBetween(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        sub.insert(0, 1);
        sub.insert(1, 2);
        sub.insert(2, 3);
        sub.insert(3, 4);

        main.insert(0, 1);
        main.insert(1, 2);
        main.insert(2, 5);
        main.insert(5, 6);
        main.insert(6, 7);
        main.insert(7, 3);
        main.insert(3, 4);
        // sub : 0 -> 1 -> 2 -> 3 -> 4 -> 8
        // main: 0 -> 1 -> 2 -> 5 -> 6 -> 7 -> 3 -> 4 -> 8
        assertDoesNotThrow(() -> cp.post(new SubSequence(main, sub)));
        assertEquals(6, sub.nNode(REQUIRED));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDisjointNodes(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        sub.insert(0, 1);
        sub.insert(1, 2);
        sub.insert(2, 3);
        sub.insert(3, 4);

        main.insert(0, 1);
        main.insert(1, 5);
        main.insert(5, 6);
        main.insert(6, 4);
        // sub : 0 -> 1 -> 2 -> 3 -> 4 -> 8
        // main: 0 -> 1 -> 5 -> 6 -> 4 -> 8
        assertDoesNotThrow(() -> cp.post(new SubSequence(main, sub)));
        assertTrue(main.isNode(2, REQUIRED));
        assertTrue(main.isNode(3, REQUIRED));
        assertFalse(main.isNode(2, MEMBER));
        assertFalse(main.isNode(3, MEMBER));
        assertFalse(sub.isNode(5, REQUIRED));
        assertFalse(sub.isNode(6, REQUIRED));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testIncoherentOrdering1(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        sub.insert(0, 1);
        sub.insert(1, 2);
        main.insert(0, 2);
        main.insert(2, 1);
        // sub : 0 -> 1 -> 2 -> 8
        // main: 0 -> 2 -> 1 -> 8
        // incoherent ordering between the sequences
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new SubSequence(main, sub)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testIncoherentOrdering2(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        sub.insert(0, 1);
        sub.insert(1, 2);
        sub.insert(2, 3);
        sub.insert(3, 4);
        sub.insert(4, 5);

        main.insert(0, 1);
        main.insert(1, 5);
        main.insert(5, 6);
        main.insert(6, 4);
        // sub : 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 8
        // main: 0 -> 1 -> 5 -> 6 -> 4 -> 8
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new SubSequence(main, sub)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRequireInMain(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        sub.insert(0, 2);
        main.insert(0, 1);
        main.insert(1, 3);
        // sub : 0 ->   2    -> 8
        // main: 0 -> 1 -> 3 -> 8
        // 2 should be set as required but cannot be inserted yet (not sure where to insert it exactly)
        cp.post(new SubSequence(main, sub));
        assertTrue(main.isNode(2, REQUIRED));
        assertFalse(main.isNode(2, MEMBER));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testExcludeInMain1(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        main.exclude(3);
        // sub : 0 -> 8
        // main: 0 -> 8
        // 3 is excluded from main and thus cannot appear in sub
        cp.post(new SubSequence(main, sub));
        assertTrue(sub.isNode(3, EXCLUDED));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testExcludeInMain2(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        // sub : 0 -> 8
        // main: 0 -> 8
        cp.post(new SubSequence(main, sub));
        cp.post(exclude(main, 3));
        // 3 is excluded from main and thus cannot appear in sub
        assertTrue(sub.isNode(3, EXCLUDED));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAddToSub1(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        main.insert(0, 1); // 0 -> 1 -> 8
        sub.require(1); // the node needs to be visited
        cp.post(new SubSequence(main, sub));
        assertTrue(sub.isNode(1, MEMBER));
        assertEquals(0, sub.memberBefore(1));
        assertEquals(8, sub.memberAfter(1));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAddToSub2(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 6, 0, 1);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 6, 0, 1);
        main.insert(0, 4);
        main.insert(4, 3);
        main.insert(3, 2);
        sub.insert(0, 2);
        sub.exclude(3);
        sub.require(4);
        // sub : 0 -> 2 -> 1
        // main: 0 -> 4 -> 3 -> 2 -> 1
        // 3 is excluded from sub, and not taken into account
        // 4 is required into sub, and should be inserted between 0 and 2 in sub
        cp.post(new SubSequence(main, sub));
        // sub : 0 -> 4 -> 2 -> 1
        assertEquals(4, sub.nNode(MEMBER));
        assertTrue(sub.isNode(4, MEMBER));
        assertEquals(0, sub.memberBefore(4));
        assertEquals(2, sub.memberAfter(4));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAddToSub3(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 6, 0, 1);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 6, 0, 1);
        main.insert(0, 4);
        main.insert(4, 5);
        main.insert(5, 3);
        main.insert(3, 2);
        sub.insert(0, 4);
        sub.insert(4, 2);
        sub.exclude(3);
        sub.require(5);
        // sub : 0 -> 4 -> 2 -> 1
        // main: 0 -> 4 -> 5 -> 3 -> 2 -> 1
        // 3 is excluded from sub, and not taken into account
        // 5 is required into sub, and should be inserted between 4 and 2 in sub
        cp.post(new SubSequence(main, sub));
        // sub : 0 -> 4 -> 5 -> 2 -> 1
        assertEquals(5, sub.nNode(MEMBER));
        assertTrue(sub.isNode(5, MEMBER));
        assertEquals(4, sub.memberBefore(5));
        assertEquals(2, sub.memberAfter(5));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRemoveInsert1(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 6, 0, 1);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 6, 0, 1);
        main.insert(0, 4);
        main.insert(4, 3);
        main.insert(3, 2);
        sub.insert(0, 2);
        // sub : 0 -> 2 -> 1
        // main: 0 -> 4 -> 3 -> 2 -> 1
        cp.post(new SubSequence(main, sub));

        for (int node: new int[] {3, 4}) {
            assertTrue(sub.hasInsert(0, node));
            assertFalse(sub.hasInsert(2, node));
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRemoveInsert2(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 6, 0, 1);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 6, 0, 1);
        main.insert(0, 2);
        main.insert(2, 3);
        main.insert(3, 4);
        sub.insert(0, 2);
        // sub : 0 -> 2 -> 1
        // main: 0 -> 2 -> 3 -> 4 -> 1
        cp.post(new SubSequence(main, sub));

        for (int node: new int[] {3, 4}) {
            assertFalse(sub.hasInsert(0, node));
            assertTrue(sub.hasInsert(2, node));
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRemoveInsert3(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 10, 0, 9);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 10, 0, 9);
        for (int i = 1 ; i < 9 ; i++) {
            main.insert(i-1, i);
        }
        sub.insert(0, 3);
        sub.insert(3, 6);
        // sub : 0 -> 3 -> 6 -> 9
        // main: 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9
        cp.post(new SubSequence(main, sub));

        for (int node: new int[] {1, 2}) {
            assertTrue(sub.hasInsert(0, node));
            assertFalse(sub.hasInsert(3, node));
            assertFalse(sub.hasInsert(6, node));
        }
        for (int node: new int[] {4, 5}) {
            assertFalse(sub.hasInsert(0, node));
            assertTrue(sub.hasInsert(3, node));
            assertFalse(sub.hasInsert(6, node));
        }
        for (int node: new int[] {7, 8}) {
            assertFalse(sub.hasInsert(0, node));
            assertFalse(sub.hasInsert(3, node));
            assertTrue(sub.hasInsert(6, node));
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRemoveInsert4(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 10, 0, 9);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 10, 0, 9);
        main.insert(0, 1);
        main.insert(1, 4);
        main.insert(4, 5);
        main.insert(5, 6);
        main.insert(6, 7);
        main.insert(7, 8);
        sub.insert(0, 3);
        sub.insert(3, 6);
        // sub : 0 -> 3 -> 6 -> 9
        // main: 0 -> 1 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9
        cp.post(new SubSequence(main, sub));

        assertTrue(main.hasInsert(0, 3));
        assertTrue(main.hasInsert(1, 3));
        assertTrue(main.hasInsert(4, 3));
        assertTrue(main.hasInsert(5, 3));
        assertFalse(main.hasInsert(6, 3));
        assertFalse(main.hasInsert(7, 3));
        assertFalse(main.hasInsert(8, 3));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoAddToSub(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        main.insert(0, 1); // 0 -> 1 -> 8
        cp.post(new SubSequence(main, sub));
        assertFalse(sub.isNode(1, REQUIRED));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInvalidSubSequence(CPSolver cp) {
        CPSeqVar main = CPFactory.makeSeqVar(cp, 9, 0, 8);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, 9, 0, 8);
        sub.insert(0, 1);
        main.exclude(1);
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new SubSequence(main, sub)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDFS1(CPSolver cp) {
        int nNodes = 6;
        CPSeqVar main = CPFactory.makeSeqVar(cp, nNodes, 0, 1);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, nNodes, 0, 1);
        sub.exclude(3);
        for (int i = 0 ; i < nNodes ; i++) {
            if (i != 3) {
                int finalI = i;
                main.getNodeVar(i).whenRequire(() -> sub.require(finalI));
                main.getNodeVar(i).whenExclude(() -> sub.exclude(finalI));
            }
        }
        cp.post(new SubSequence(main, sub));
        DFSearch search = makeDfs(cp, firstFail(main));
        search.onSolution(() -> {
            assertTrue(sub.isFixed());
            int predMain = main.start();
            int pred = predMain;
            while (predMain != main.end()) {
                int nodeMain = main.memberAfter(pred);
                if (nodeMain != 3) {
                    assertTrue(sub.isNode(nodeMain, MEMBER), "Failed to find node " + nodeMain + " in sub-sequence:" +
                            "\nmain: " + main + "\nsub:  " + sub);
                    assertEquals(predMain, sub.memberBefore(nodeMain));
                    predMain = nodeMain;
                }
                pred = nodeMain;
            }
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
    @MethodSource("getSolver")
    public void testDFS2(CPSolver cp) {
        int nNodes = 6;
        CPSeqVar main = CPFactory.makeSeqVar(cp, nNodes, 0, 1);
        CPSeqVar sub = CPFactory.makeSeqVar(cp, nNodes, 0, 1);
        sub.exclude(3);
        cp.post(new SubSequence(main, sub));
        DFSearch search = makeDfs(cp, firstFail(sub));
        search.onSolution(() -> {
            assertTrue(main.isNode(3, POSSIBLE));
            assertFalse(main.isFixed());
            int nMain = main.nNode(MEMBER);
            int nSub = sub.nNode(MEMBER);
            assertEquals(nMain, nSub);
            int[] nodesMain = new int[nMain];
            int[] nodesSub = new int[nSub];
            main.fillNode(nodesMain, MEMBER_ORDERED);
            sub.fillNode(nodesSub, MEMBER_ORDERED);
            assertArrayEquals(nodesMain, nodesSub);
        });
        int nSolutions = 0; // number of sequences that can be constructed
        for (int n = 0; n <= nNodes - 3; n++) {
            // visit n nodes within the nNodes-3 possible (omitting the start and end)
            //  * arrangements for the nNodes
            nSolutions += nCk(nNodes-3, n) * fact(n);
        }
        SearchStatistics statistics = search.solve();
        assertEquals(nSolutions, statistics.numberOfSolutions());
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
