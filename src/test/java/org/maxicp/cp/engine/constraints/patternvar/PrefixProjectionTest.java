/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.SeqMiningTestUtils;
import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.search.DFSearch;
import org.maxicp.util.exception.InconsistencyException;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.makeIntVarArray;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class PrefixProjectionTest extends CPSolverTest {

    /**
     * Static positional branching: fix the first unfixed position to its
     * minimum value (epsilon first), or remove it.
     */
    static Supplier<Runnable[]> positionalBranching(CPSolver cp, CPIntVar[] p) {
        return () -> {
            CPIntVar next = null;
            for (CPIntVar position : p) {
                if (!position.isFixed()) {
                    next = position;
                    break;
                }
            }
            if (next == null)
                return EMPTY;
            CPIntVar selected = next;
            int v = selected.min();
            return branch(
                    () -> {
                        selected.fix(v);
                        cp.fixPoint();
                    },
                    () -> {
                        selected.remove(v);
                        cp.fixPoint();
                    });
        };
    }

    static Map<String, Integer> collectSolutions(CPSolver cp, CPIntVar[] p, PrefixProjection constraint) {
        Map<String, Integer> sols = new HashMap<>();
        int[] buf = new int[p.length];
        DFSearch dfs = CPFactory.makeDfs(cp, positionalBranching(cp, p));
        dfs.onSolution(() -> {
            int len = 0;
            while (len < p.length && p[len].min() != 0) {
                buf[len] = p[len].min();
                len++;
            }
            String key = SeqMiningTestUtils.join(buf, len);
            assertNull(sols.put(key, constraint.patternSupport()), "pattern enumerated twice: " + key);
        });
        dfs.solve();
        return sols;
    }

    static CPIntVar[] positionalVars(CPSolver cp, SequenceDb db) {
        return makeIntVarArray(cp, db.maxSeqLength(), db.nSymbols());
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testReferenceOutput(Supplier<CPSolver> solverSupplier) {
        SequenceDb db = SequenceDb.readSpmf("data/MINING/SEQUENCE/test.spmf");
        for (int theta : new int[]{2, 3}) {
            Map<String, Integer> expected =
                    SeqMiningTestUtils.readSols("data/MINING/SEQUENCE/oracle/test_prefixspan_" + theta + ".txt");
            CPSolver cp = solverSupplier.get();
            CPIntVar[] p = positionalVars(cp, db);
            PrefixProjection constraint = new PrefixProjection(p, theta, db);
            cp.post(constraint);
            Map<String, Integer> sols = collectSolutions(cp, p, constraint);
            assertEquals(db.nSeqs(), sols.remove("")); // empty pattern covers everything
            assertEquals(expected, sols, "theta=" + theta);
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRootFiltering(CPSolver cp) {
        // symbol 3 appears in one sequence only
        SequenceDb db = new SequenceDb("toy", new int[][]{{1, 2}, {2, 1}, {2, 3}});
        CPIntVar[] p = positionalVars(cp, db);
        cp.post(new PrefixProjection(p, 2, db));
        for (CPIntVar position : p) {
            assertFalse(position.contains(3));
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testInfeasibleThreshold(CPSolver cp) {
        SequenceDb db = new SequenceDb("toy", new int[][]{{1, 2}});
        CPIntVar[] p = positionalVars(cp, db);
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new PrefixProjection(p, 2, db)));
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testAgainstBruteForce(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 10; seed++) {
            Random rand = new Random(seed);
            int nSeqs = 1 + rand.nextInt(6);
            int nSymbols = 2 + rand.nextInt(4);
            SequenceDb db = SeqMiningTestUtils.randomDb(rand, nSeqs, nSymbols, 4);
            int theta = 1 + rand.nextInt(2);
            Map<String, Integer> expected = SeqMiningTestUtils.frequentSequences(db, theta, db.maxSeqLength());

            CPSolver cp = solverSupplier.get();
            if (db.maxSeqLength() == 0) {
                // only the empty pattern exists; the positional model needs at least one variable
                assertEquals(nSeqs >= theta ? 1 : 0, expected.size(), "seed=" + seed);
                continue;
            }
            CPIntVar[] p = positionalVars(cp, db);
            PrefixProjection constraint = new PrefixProjection(p, theta, db);
            try {
                cp.post(constraint);
            } catch (InconsistencyException e) {
                assertTrue(nSeqs < theta, "seed=" + seed);
                assertTrue(expected.isEmpty(), "seed=" + seed);
                continue;
            }
            assertEquals(expected, collectSolutions(cp, p, constraint), "seed=" + seed);
        }
    }
}
