/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.constraints.seqvar.RelaxedSequence;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.MinimizeObjectiveSum;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class LightBinaryKnapsackAndObjectiveTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testLightBinaryKnapsackInitAndPropagation(CPSolver cp) {
        CPBoolVar[] items = new CPBoolVar[4];
        for (int i = 0; i < 4; i++) {
            items[i] = CPFactory.makeBoolVar(cp);
        }
        // Pre-fix last item (index 3) to true before posting to cover init() fixed-item branch
        items[3].fix(true);

        int[] weights = new int[]{3, 4, 8, 2};
        CPIntVar load = CPFactory.makeIntVar(cp, 5, 7);

        cp.post(new LightBinaryKnapsack(items, weights, load));

        // Fix item 0 to false; then requiredLoad = 2 (item 3). load is in [5..7].
        items[0].fix(false);
        cp.fixPoint();

        // Item 2 (weight 8): 2 + 8 = 10 > 7 -> item 2 must be fixed to false!
        // Once item 2 is false, possibleLoad becomes 2 + 4 = 6.
        // Since load.min() is 5, minWeight = 6 - 5 = 1 < 4 (weight of item 1) -> item 1 must be fixed to true!
        // Then requiredLoad = 6, possibleLoad = 6 -> load is fixed to 6!
        assertTrue(items[2].isFalse());
        assertTrue(items[1].isTrue());
        assertTrue(load.isFixed());
        assertEquals(6, load.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testLightBinaryKnapsackIncrementalAssignAndFilter(CPSolver cp) {
        CPBoolVar[] items = new CPBoolVar[3];
        for (int i = 0; i < 3; i++) {
            items[i] = CPFactory.makeBoolVar(cp);
        }
        int[] weights = new int[]{2, 3, 5};
        CPIntVar load = CPFactory.makeIntVar(cp, 0, 10);

        cp.post(new LightBinaryKnapsack(items, weights, load));
        assertFalse(items[0].isFixed());
        assertFalse(items[1].isFixed());
        assertFalse(items[2].isFixed());

        items[0].fix(true);
        items[1].fix(false);
        cp.fixPoint();

        assertEquals(2, load.min());
        assertEquals(7, load.max());

        load.removeAbove(5);
        cp.fixPoint();
        assertTrue(items[2].isFalse());
        assertEquals(2, load.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testLightBinaryKnapsackRandomPreserveSolutions(CPSolver cp) {
        int n = 7;
        Random r = new Random(42);
        for (int iter = 0; iter < 5; iter++) {
            CPBoolVar[] x = new CPBoolVar[n];
            int[] w = new int[n];
            CPIntVar[] loadExpr = new CPIntVar[n];
            for (int i = 0; i < n; i++) {
                x[i] = CPFactory.makeBoolVar(cp);
                w[i] = r.nextInt(6) + 1;
                loadExpr[i] = CPFactory.mul(x[i], w[i]);
            }
            CPIntVar load = CPFactory.makeIntVar(cp, 3, 12);

            cp.getStateManager().saveState();
            cp.post(new LightBinaryKnapsack(x, w, load));
            DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));
            SearchStatistics statLight = dfs.solve();
            cp.getStateManager().restoreState();

            cp.getStateManager().saveState();
            cp.post(CPFactory.sum(loadExpr, load));
            SearchStatistics statDecomp = dfs.solve();
            cp.getStateManager().restoreState();

            assertEquals(statDecomp.numberOfSolutions(), statLight.numberOfSolutions());
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRelaxedSequenceValidationAndPost(CPSolver cp) {
        // 5 nodes: start = 0, end = 4, internal nodes = 1, 2, 3
        CPSeqVar seq = CPFactory.makeSeqVar(cp, 5, 0, 4);

        // Wrong start node (disable assertions check if needed or catch Throwable/AssertionError/IllegalArgumentException)
        assertThrows(Throwable.class,
                () -> new RelaxedSequence(seq, new int[]{1, 0, 2, 3, 4}, Set.of()));
        // Wrong end node
        assertThrows(Throwable.class,
                () -> new RelaxedSequence(seq, new int[]{0, 1, 2, 4, 3}, Set.of()));
        // Duplicate node
        assertThrows(IllegalArgumentException.class,
                () -> new RelaxedSequence(seq, new int[]{0, 1, 1, 2, 4}, Set.of()));

        // Pre-insert node 1 after 0 so we test both already-MEMBER and not-yet-MEMBER branches in post()
        seq.insert(0, 1);
        // Relax node 2, keep nodes 1 and 3 in order 0 -> 1 -> 3 -> 4
        RelaxedSequence rel = new RelaxedSequence(seq, new int[]{0, 1, 2, 3, 4}, Set.of(2));
        cp.post(rel);

        assertEquals(1, seq.memberAfter(0));
        assertEquals(3, seq.memberAfter(1));
        assertEquals(4, seq.memberAfter(3));

        // Node 2 is still insertable in 3 positions (after 0, after 1, after 3) or excluded (1 way) = 4 solutions
        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(seq));
        SearchStatistics stats = dfs.solve();
        assertEquals(4, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMinimizeObjectiveSumVOLNS(CPSolver cp) {
        CPIntVar x0 = CPFactory.makeIntVar(cp, 1, 5);
        CPIntVar x1 = CPFactory.makeIntVar(cp, 2, 6);
        CPIntVar x2 = CPFactory.makeIntVar(cp, 0, 4);
        CPIntVar[] vars = new CPIntVar[]{x0, x1, x2};

        cp.post(CPFactory.ge(CPFactory.sum(vars), 5));

        MinimizeObjectiveSum obj = new MinimizeObjectiveSum(vars);
        assertEquals(Integer.MAX_VALUE - 1, obj.getBound());

        // Start with a suboptimal assignment (4, 5, 3) -> sum = 12
        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(vars));

        // 1. Solve with initial suboptimal fix (4, 5, 3)
        SearchStatistics s1 = dfs.optimizeSubjectTo(obj, stats -> stats.numberOfSolutions() >= 1, () -> {
            cp.post(CPFactory.eq(x0, 4));
            cp.post(CPFactory.eq(x1, 5));
            cp.post(CPFactory.eq(x2, 3));
        });
        assertEquals(1, s1.numberOfSolutions());
        assertEquals(11, obj.getBound()); // tightened from 12 to 11

        // 2. Test weakTightenAll + strongTigthenWorseTerm (worse term is x1 with bound 5 -> becomes 4)
        obj.weakTightenAll();
        obj.strongTigthenWorseTerm();
        SearchStatistics s2 = dfs.optimizeSubjectTo(obj, stats -> stats.numberOfSolutions() >= 1, () -> {});
        assertEquals(1, s2.numberOfSolutions());
        assertTrue(obj.getBound() <= 11);

        // 3. Test relax() and strongTightenSum() and filter() to find global optimum (sum = 5 -> bound = 4)
        obj.relax();
        obj.strongTightenSum();
        SearchStatistics s3 = dfs.optimize(obj);
        assertTrue(s3.numberOfSolutions() >= 1);
        assertEquals(4, obj.getBound());
    }
}
