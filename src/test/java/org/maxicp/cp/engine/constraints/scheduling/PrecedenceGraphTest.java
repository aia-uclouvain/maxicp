/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

/**
 * Tests for {@link PrecedenceGraph}.
 */
class PrecedenceGraphTest extends CPSolverTest {

    // ===================== Basic precedence propagation =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBasicPrecedence(CPSolver cp) {
        // A(dur=3) -> B(dur=2), horizon [0..20]
        CPIntervalVar a = makeIntervalVar(cp, 3);
        CPIntervalVar b = makeIntervalVar(cp, 2);
        a.setPresent();
        b.setPresent();
        a.setEndMax(20);
        b.setEndMax(20);

        PrecedenceGraph graph = new PrecedenceGraph(a, b);
        cp.post(graph);

        graph.addPrecedence(0, 1); // A -> B

        assertTrue(graph.hasPrecedence(0, 1));
        // est(B) >= est(A) + dur(A) = 0 + 3 = 3
        assertTrue(b.startMin() >= 3, "B should start at 3 or later, got " + b.startMin());
        // endMax(A) <= endMax(B) - dur(B) = 20 - 2 = 18
        assertTrue(a.endMax() <= 18, "A should end at 18 or earlier, got " + a.endMax());
    }

    // ===================== Transitive closure =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTransitiveClosure(CPSolver cp) {
        // A(dur=2) -> B(dur=3) -> C(dur=1), horizon [0..30]
        CPIntervalVar a = makeIntervalVar(cp, 2);
        CPIntervalVar b = makeIntervalVar(cp, 3);
        CPIntervalVar c = makeIntervalVar(cp, 1);
        a.setPresent();
        b.setPresent();
        c.setPresent();
        a.setEndMax(30);
        b.setEndMax(30);
        c.setEndMax(30);

        PrecedenceGraph graph = new PrecedenceGraph(a, b, c);
        cp.post(graph);

        graph.addPrecedence(0, 1); // A -> B
        graph.addPrecedence(1, 2); // B -> C

        // Transitive: A -> C
        assertTrue(graph.hasPrecedence(0, 2), "Transitive closure should add A -> C");

        // est(B) >= 2, est(C) >= 2 + 3 = 5
        assertTrue(b.startMin() >= 2);
        assertTrue(c.startMin() >= 5, "C should start at 5 or later, got " + c.startMin());

        // endMax(B) <= 30 - 1 = 29, endMax(A) <= 29 - 3 = 26
        assertTrue(b.endMax() <= 29);
        assertTrue(a.endMax() <= 27, "A should end at 27 or earlier, got " + a.endMax());
    }

    // ===================== Cycle detection =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCycleDetectionDirect(CPSolver cp) {
        CPIntervalVar a = makeIntervalVar(cp, 2);
        CPIntervalVar b = makeIntervalVar(cp, 2);
        a.setPresent();
        b.setPresent();
        a.setEndMax(20);
        b.setEndMax(20);

        PrecedenceGraph graph = new PrecedenceGraph(a, b);
        cp.post(graph);

        graph.addPrecedence(0, 1); // A -> B
        assertThrows(InconsistencyException.class, () -> graph.addPrecedence(1, 0)); // B -> A = cycle
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCycleDetectionTransitive(CPSolver cp) {
        CPIntervalVar a = makeIntervalVar(cp, 1);
        CPIntervalVar b = makeIntervalVar(cp, 1);
        CPIntervalVar c = makeIntervalVar(cp, 1);
        a.setPresent();
        b.setPresent();
        c.setPresent();
        a.setEndMax(20);
        b.setEndMax(20);
        c.setEndMax(20);

        PrecedenceGraph graph = new PrecedenceGraph(a, b, c);
        cp.post(graph);

        graph.addPrecedence(0, 1); // A -> B
        graph.addPrecedence(1, 2); // B -> C
        // Now A -> B -> C (transitive: A -> C)
        assertThrows(InconsistencyException.class, () -> graph.addPrecedence(2, 0)); // C -> A = cycle
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSelfLoopDetection(CPSolver cp) {
        CPIntervalVar a = makeIntervalVar(cp, 2);
        a.setPresent();
        a.setEndMax(20);

        PrecedenceGraph graph = new PrecedenceGraph(a);
        cp.post(graph);

        assertThrows(InconsistencyException.class, () -> graph.addPrecedence(0, 0)); // self-loop
    }

    // ===================== Setup / transition times =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSetupTimes(CPSolver cp) {
        // A(dur=2) --(setup=3)--> B(dur=2), horizon [0..20]
        CPIntervalVar a = makeIntervalVar(cp, 2);
        CPIntervalVar b = makeIntervalVar(cp, 2);
        a.setPresent();
        b.setPresent();
        a.setEndMax(20);
        b.setEndMax(20);

        int[][] setup = new int[2][2];
        setup[0][1] = 3; // A -> B needs 3 time units gap

        PrecedenceGraph graph = new PrecedenceGraph(setup, a, b);
        cp.post(graph);

        graph.addPrecedence(0, 1); // A -> B

        // est(B) >= est(A) + dur(A) + setup(A,B) = 0 + 2 + 3 = 5
        assertTrue(b.startMin() >= 5, "B should start at 5 or later with setup time, got " + b.startMin());

        // endMax(A) <= endMax(B) - dur(B) - setup(A,B) = 20 - 2 - 3 = 15
        assertTrue(a.endMax() <= 15, "A should end at 15 or earlier with setup time, got " + a.endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSetupTimesTransitive(CPSolver cp) {
        // A(dur=1) --(setup=2)--> B(dur=1) --(setup=3)--> C(dur=1), horizon [0..30]
        CPIntervalVar a = makeIntervalVar(cp, 1);
        CPIntervalVar b = makeIntervalVar(cp, 1);
        CPIntervalVar c = makeIntervalVar(cp, 1);
        a.setPresent();
        b.setPresent();
        c.setPresent();
        a.setEndMax(30);
        b.setEndMax(30);
        c.setEndMax(30);

        int[][] setup = new int[3][3];
        setup[0][1] = 2; // A -> B setup
        setup[1][2] = 3; // B -> C setup

        PrecedenceGraph graph = new PrecedenceGraph(setup, a, b, c);
        cp.post(graph);

        graph.addPrecedence(0, 1); // A -> B
        graph.addPrecedence(1, 2); // B -> C

        // est(B) >= 0 + 1 + 2 = 3
        assertTrue(b.startMin() >= 3);
        // est(C) >= 3 + 1 + 3 = 7
        assertTrue(c.startMin() >= 7, "C should start at 7 or later, got " + c.startMin());
    }

    // ===================== Reversibility =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testReversibility(CPSolver cp) {
        CPIntervalVar a = makeIntervalVar(cp, 3);
        CPIntervalVar b = makeIntervalVar(cp, 2);
        CPIntervalVar c = makeIntervalVar(cp, 1);
        a.setPresent();
        b.setPresent();
        c.setPresent();
        a.setEndMax(20);
        b.setEndMax(20);
        c.setEndMax(20);

        PrecedenceGraph graph = new PrecedenceGraph(a, b, c);
        cp.post(graph);

        graph.addPrecedence(0, 1); // A -> B
        assertTrue(graph.hasPrecedence(0, 1));
        int estB_after_AB = b.startMin();
        assertTrue(estB_after_AB >= 3);

        // Save state
        cp.getStateManager().saveState();

        graph.addPrecedence(1, 2); // B -> C
        assertTrue(graph.hasPrecedence(1, 2));
        assertTrue(graph.hasPrecedence(0, 2)); // transitive
        assertTrue(c.startMin() >= 5);

        // Restore state
        cp.getStateManager().restoreState();

        // B -> C should be undone, but A -> B should remain
        assertTrue(graph.hasPrecedence(0, 1), "A -> B should survive restore");
        assertFalse(graph.hasPrecedence(1, 2), "B -> C should be undone by restore");
        assertFalse(graph.hasPrecedence(0, 2), "A -> C should be undone by restore");
    }

    // ===================== Detectable precedences =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDetectablePrecedences(CPSolver cp) {
        // Two activities that must not overlap on a tight horizon.
        // A(dur=3) forced to start at 0 (startMax=0). B(dur=3), horizon [0..6].
        // ect(A) = 0 + 3 = 3.
        // "B before A" requires: est(B) + dur(B) <= lst(A).
        //   lst(A) = endMax(A) - dur(A) = 6 - 3 = 3, but startMax(A) = 0 so lst(A) = 0.
        //   est(B) + dur(B) = 0 + 3 = 3 > 0 → B cannot precede A → A must precede B.
        // "A before B" requires: ect(A) <= endMax(B) - dur(B) = 6 - 3 = 3. 3 <= 3 → feasible.
        CPIntervalVar a = makeIntervalVar(cp, 3);
        CPIntervalVar b = makeIntervalVar(cp, 3);
        a.setPresent();
        b.setPresent();
        a.setEndMax(6);
        b.setEndMax(6);

        // Force A early
        a.setStartMax(0); // A must start at 0, end at 3

        PrecedenceGraph graph = new PrecedenceGraph(a, b);
        cp.post(graph); // propagation should detect A -> B

        assertTrue(graph.hasPrecedence(0, 1),
                "Detectable precedence: A must precede B since B cannot start before A ends");
        assertTrue(b.startMin() >= 3, "B should start at 3 or later");
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDetectablePrecedenceFail(CPSolver cp) {
        // Two activities, both forced to the same time, neither can precede the other
        // A(dur=5) at [0..0], B(dur=5) at [0..0], horizon = 4
        // ect(A) = 5 > lst(B) = -1 and ect(B) = 5 > lst(A) = -1
        // Neither ordering works → fail
        CPIntervalVar a = makeIntervalVar(cp, 5);
        CPIntervalVar b = makeIntervalVar(cp, 5);
        a.setPresent();
        b.setPresent();
        a.setStartMax(0);
        a.setEndMax(5);
        b.setStartMax(0);
        b.setEndMax(5);

        PrecedenceGraph graph = new PrecedenceGraph(a, b);
        assertThrows(InconsistencyException.class, () -> cp.post(graph),
                "Should fail: neither ordering is feasible");
    }

    // ===================== Query methods =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testQueryMethods(CPSolver cp) {
        CPIntervalVar a = makeIntervalVar(cp, 1);
        CPIntervalVar b = makeIntervalVar(cp, 1);
        CPIntervalVar c = makeIntervalVar(cp, 1);
        a.setPresent();
        b.setPresent();
        c.setPresent();
        a.setEndMax(20);
        b.setEndMax(20);
        c.setEndMax(20);

        PrecedenceGraph graph = new PrecedenceGraph(a, b, c);
        cp.post(graph);

        assertEquals(3, graph.size());
        assertEquals(0, graph.nPredecessors(0));
        assertEquals(0, graph.nSuccessors(0));

        graph.addPrecedence(0, 1); // A -> B
        graph.addPrecedence(0, 2); // A -> C

        assertEquals(0, graph.nPredecessors(0));
        assertEquals(2, graph.nSuccessors(0));
        assertEquals(1, graph.nPredecessors(1)); // B has A as predecessor
        assertEquals(1, graph.nPredecessors(2)); // C has A as predecessor

        int[] buf = new int[3];
        int nSucc = graph.fillSuccessors(0, buf);
        assertEquals(2, nSucc);
    }

    // ===================== Duplicate precedence (idempotent) =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDuplicatePrecedence(CPSolver cp) {
        CPIntervalVar a = makeIntervalVar(cp, 2);
        CPIntervalVar b = makeIntervalVar(cp, 2);
        a.setPresent();
        b.setPresent();
        a.setEndMax(20);
        b.setEndMax(20);

        PrecedenceGraph graph = new PrecedenceGraph(a, b);
        cp.post(graph);

        graph.addPrecedence(0, 1);
        graph.addPrecedence(0, 1); // duplicate - should be a no-op

        assertTrue(graph.hasPrecedence(0, 1));
        assertEquals(1, graph.nPredecessors(1));
    }

    // ===================== Integration: enumeration with search =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEnumerationWithSearch(CPSolver cp) {
        // 3 activities of duration 1 on horizon [0..2]
        // With precedence graph (no initial edges), activities form a disjunctive resource.
        // Number of valid orderings = 3! = 6
        int n = 3;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, 1);
            intervals[i].setPresent();
            intervals[i].setEndMax(n);
        }

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        cp.post(graph);

        DFSearch dfs = CPFactory.makeDfs(cp, branchOnPresentStarts(intervals));
        SearchStatistics stats = dfs.solve();

        assertEquals(6, stats.numberOfSolutions(), "3 tasks of dur 1 on horizon 3 should have 3! = 6 solutions");
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEnumerationWithPrecedence(CPSolver cp) {
        // 3 activities of duration 1 on horizon [0..2], with A -> B
        // Valid orderings: A before B, C can be anywhere not conflicting
        // Positions: A=0,B=1,C=2 | A=0,C=1,B=2 | C=0,A=1,B=2 → 3 solutions
        int n = 3;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, 1);
            intervals[i].setPresent();
            intervals[i].setEndMax(n);
        }

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        cp.post(graph);
        graph.addPrecedence(0, 1); // A -> B

        DFSearch dfs = CPFactory.makeDfs(cp, branchOnPresentStarts(intervals));
        SearchStatistics stats = dfs.solve();

        assertEquals(3, stats.numberOfSolutions(),
                "3 tasks with A->B on horizon 3 should have 3 solutions");
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEnumerationFullChain(CPSolver cp) {
        // A -> B -> C, each dur=1, horizon [0..2]
        // Only 1 solution: A=0, B=1, C=2
        int n = 3;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, 1);
            intervals[i].setPresent();
            intervals[i].setEndMax(n);
        }

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        cp.post(graph);
        graph.addPrecedence(0, 1); // A -> B
        graph.addPrecedence(1, 2); // B -> C

        DFSearch dfs = CPFactory.makeDfs(cp, branchOnPresentStarts(intervals));
        SearchStatistics stats = dfs.solve();

        assertEquals(1, stats.numberOfSolutions(),
                "Full chain A->B->C on horizon 3 should have exactly 1 solution");
        assertEquals(0, intervals[0].startMin());
        assertEquals(1, intervals[1].startMin());
        assertEquals(2, intervals[2].startMin());
    }

    // ===================== Optional / absent activities =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAbsentActivityIgnored(CPSolver cp) {
        CPIntervalVar a = makeIntervalVar(cp, 5);
        CPIntervalVar b = makeIntervalVar(cp, 2);
        CPIntervalVar c = makeIntervalVar(cp, 3); // will be absent
        a.setPresent();
        b.setPresent();
        a.setEndMax(200);
        b.setEndMax(200);
        c.setEndMax(200);

        // Set c absent BEFORE posting so detectable precedences don't trigger for it
        c.setAbsent();
        cp.fixPoint();

        PrecedenceGraph graph = new PrecedenceGraph(a, b, c);
        cp.post(graph);

        graph.addPrecedence(0, 2); // A -> C (but C is absent)
        graph.addPrecedence(2, 1); // C -> B (but C is absent)

        // A -> B is added transitively through C, but C is absent.
        // The forward propagation skips absent C, so est(B) should only be pushed by A's
        // own bounds through the transitive A -> B edge: est(B) >= est(A) + dur(A) = 5
        assertTrue(b.startMin() >= 5, "B should be pushed by A (duration 5) through transitive A->B, got " + b.startMin());
    }

    // ===================== Chain of 4 with setup times =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testChainOfFourWithSetup(CPSolver cp) {
        // A(1) --s=1--> B(1) --s=1--> C(1) --s=1--> D(1)
        // est(A)=0, est(B)=0+1+1=2, est(C)=2+1+1=4, est(D)=4+1+1=6
        int n = 4;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, 1);
            intervals[i].setPresent();
            intervals[i].setEndMax(30);
        }

        int[][] setup = new int[n][n];
        setup[0][1] = 1;
        setup[1][2] = 1;
        setup[2][3] = 1;

        PrecedenceGraph graph = new PrecedenceGraph(setup, intervals);
        cp.post(graph);

        graph.addPrecedence(0, 1);
        graph.addPrecedence(1, 2);
        graph.addPrecedence(2, 3);

        assertEquals(0, intervals[0].startMin());
        assertTrue(intervals[1].startMin() >= 2, "B est should be >= 2, got " + intervals[1].startMin());
        assertTrue(intervals[2].startMin() >= 4, "C est should be >= 4, got " + intervals[2].startMin());
        assertTrue(intervals[3].startMin() >= 6, "D est should be >= 6, got " + intervals[3].startMin());
    }

    // ===================== Precedence added in reverse order =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testPrecedenceReverseOrder(CPSolver cp) {
        // Add B -> C first, then A -> B. Transitive A -> C should still hold.
        CPIntervalVar a = makeIntervalVar(cp, 2);
        CPIntervalVar b = makeIntervalVar(cp, 2);
        CPIntervalVar c = makeIntervalVar(cp, 2);
        a.setPresent();
        b.setPresent();
        c.setPresent();
        a.setEndMax(30);
        b.setEndMax(30);
        c.setEndMax(30);

        PrecedenceGraph graph = new PrecedenceGraph(a, b, c);
        cp.post(graph);

        graph.addPrecedence(1, 2); // B -> C
        graph.addPrecedence(0, 1); // A -> B

        assertTrue(graph.hasPrecedence(0, 2), "Transitive A -> C should be deduced");
        assertTrue(c.startMin() >= 4, "C est should be >= 4 (est(B)+dur(B)=2+2), got " + c.startMin());
    }

    // ===================== Diamond pattern =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testDiamondPattern(CPSolver cp) {
        //     B
        //    / \
        //   A   D
        //    \ /
        //     C
        // A -> B, A -> C, B -> D, C -> D
        CPIntervalVar a = makeIntervalVar(cp, 1);
        CPIntervalVar b = makeIntervalVar(cp, 2);
        CPIntervalVar c = makeIntervalVar(cp, 3);
        CPIntervalVar d = makeIntervalVar(cp, 1);
        a.setPresent();
        b.setPresent();
        c.setPresent();
        d.setPresent();
        a.setEndMax(30);
        b.setEndMax(30);
        c.setEndMax(30);
        d.setEndMax(30);

        PrecedenceGraph graph = new PrecedenceGraph(a, b, c, d);
        cp.post(graph);

        graph.addPrecedence(0, 1); // A -> B
        graph.addPrecedence(0, 2); // A -> C
        graph.addPrecedence(1, 3); // B -> D
        graph.addPrecedence(2, 3); // C -> D

        // est(D) = max(est(B)+dur(B), est(C)+dur(C))
        // est(B) = 1, est(C) = 1
        // est(D) >= max(1+2, 1+3) = max(3, 4) = 4
        assertTrue(d.startMin() >= 4, "D est should be >= 4, got " + d.startMin());

        // A is predecessor of D (transitive)
        assertTrue(graph.hasPrecedence(0, 3));
    }

    // ===================== Tail (Q) value tests =====================

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTailBasic(CPSolver cp) {
        // A(dur=3) -> B(dur=2), horizon [0..20]
        CPIntervalVar a = makeIntervalVar(cp, 3);
        CPIntervalVar b = makeIntervalVar(cp, 2);
        a.setPresent();
        b.setPresent();
        a.setEndMax(20);
        b.setEndMax(20);

        PrecedenceGraph graph = new PrecedenceGraph(a, b);
        cp.post(graph);

        // No precedences yet: both tails are 0
        assertEquals(0, graph.getTail(0));
        assertEquals(0, graph.getTail(1));

        graph.addPrecedence(0, 1); // A -> B

        // tail(A) = dur(B) + tail(B) = 2 + 0 = 2
        assertEquals(2, graph.getTail(0), "tail(A) after A->B");
        // tail(B) = 0 (no successors)
        assertEquals(0, graph.getTail(1), "tail(B) after A->B");
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTailChain(CPSolver cp) {
        // A(dur=2) -> B(dur=3) -> C(dur=1), horizon [0..30]
        CPIntervalVar a = makeIntervalVar(cp, 2);
        CPIntervalVar b = makeIntervalVar(cp, 3);
        CPIntervalVar c = makeIntervalVar(cp, 1);
        a.setPresent();
        b.setPresent();
        c.setPresent();
        a.setEndMax(30);
        b.setEndMax(30);
        c.setEndMax(30);

        PrecedenceGraph graph = new PrecedenceGraph(a, b, c);
        cp.post(graph);

        graph.addPrecedence(0, 1); // A -> B
        // tail(A) = dur(B) + tail(B) = 3 + 0 = 3
        assertEquals(3, graph.getTail(0), "tail(A) after A->B");

        graph.addPrecedence(1, 2); // B -> C
        // tail(B) = dur(C) + tail(C) = 1 + 0 = 1
        assertEquals(1, graph.getTail(1), "tail(B) after B->C");
        // tail(A) = dur(B) + tail(B) = 3 + 1 = 4 (updated via backward propagation)
        assertEquals(4, graph.getTail(0), "tail(A) after B->C");
        // tail(C) = 0
        assertEquals(0, graph.getTail(2), "tail(C) after chain");
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTailDiamond(CPSolver cp) {
        //     B(dur=2)
        //    / \
        //   A   D(dur=1)
        //    \ /
        //     C(dur=3)
        CPIntervalVar a = makeIntervalVar(cp, 1);
        CPIntervalVar b = makeIntervalVar(cp, 2);
        CPIntervalVar c = makeIntervalVar(cp, 3);
        CPIntervalVar d = makeIntervalVar(cp, 1);
        a.setPresent();
        b.setPresent();
        c.setPresent();
        d.setPresent();
        a.setEndMax(30);
        b.setEndMax(30);
        c.setEndMax(30);
        d.setEndMax(30);

        PrecedenceGraph graph = new PrecedenceGraph(a, b, c, d);
        cp.post(graph);

        graph.addPrecedence(0, 1); // A -> B
        graph.addPrecedence(0, 2); // A -> C
        graph.addPrecedence(1, 3); // B -> D
        graph.addPrecedence(2, 3); // C -> D

        // tail(D) = 0
        assertEquals(0, graph.getTail(3));
        // tail(B) = dur(D) + tail(D) = 1 + 0 = 1
        assertEquals(1, graph.getTail(1));
        // tail(C) = dur(D) + tail(D) = 1 + 0 = 1
        assertEquals(1, graph.getTail(2));
        // tail(A) = max(dur(B)+tail(B), dur(C)+tail(C)) = max(2+1, 3+1) = max(3,4) = 4
        assertEquals(4, graph.getTail(0), "tail(A) should be 4 via longest path A->C->D");
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTailWithSetupTimes(CPSolver cp) {
        // A(dur=1) --setup=2--> B(dur=1), horizon [0..20]
        CPIntervalVar a = makeIntervalVar(cp, 1);
        CPIntervalVar b = makeIntervalVar(cp, 1);
        a.setPresent();
        b.setPresent();
        a.setEndMax(20);
        b.setEndMax(20);

        int[][] setup = new int[2][2];
        setup[0][1] = 2;

        PrecedenceGraph graph = new PrecedenceGraph(setup, a, b);
        cp.post(graph);

        graph.addPrecedence(0, 1); // A -> B

        // tail(A) = setup(A,B) + dur(B) + tail(B) = 2 + 1 + 0 = 3
        assertEquals(3, graph.getTail(0), "tail(A) with setup time");
        assertEquals(0, graph.getTail(1), "tail(B)");
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTailReversibility(CPSolver cp) {
        CPIntervalVar a = makeIntervalVar(cp, 3);
        CPIntervalVar b = makeIntervalVar(cp, 2);
        CPIntervalVar c = makeIntervalVar(cp, 1);
        a.setPresent();
        b.setPresent();
        c.setPresent();
        a.setEndMax(20);
        b.setEndMax(20);
        c.setEndMax(20);

        PrecedenceGraph graph = new PrecedenceGraph(a, b, c);
        cp.post(graph);

        graph.addPrecedence(0, 1); // A -> B
        assertEquals(2, graph.getTail(0), "tail(A) after A->B");

        cp.getStateManager().saveState();

        graph.addPrecedence(1, 2); // B -> C
        assertEquals(3, graph.getTail(0), "tail(A) after B->C");
        assertEquals(1, graph.getTail(1), "tail(B) after B->C");

        cp.getStateManager().restoreState();

        // Tail should be restored
        assertEquals(2, graph.getTail(0), "tail(A) after restore");
        assertEquals(0, graph.getTail(1), "tail(B) after restore");
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTailByVarReference(CPSolver cp) {
        CPIntervalVar a = makeIntervalVar(cp, 5);
        CPIntervalVar b = makeIntervalVar(cp, 3);
        a.setPresent();
        b.setPresent();
        a.setEndMax(20);
        b.setEndMax(20);

        PrecedenceGraph graph = new PrecedenceGraph(a, b);
        cp.post(graph);

        graph.addPrecedence(0, 1);

        assertEquals(3, graph.getTail(a), "getTail by var ref");
        assertEquals(3, graph.getQ(a), "getQ alias");
        assertEquals(0, graph.getTail(b));
    }
}


