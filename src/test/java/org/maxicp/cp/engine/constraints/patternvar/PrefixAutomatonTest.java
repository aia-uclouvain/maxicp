/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPSeqPatternVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.mining.Dfa;
import org.maxicp.cp.engine.core.mining.SeqMiningTestUtils;
import org.maxicp.cp.engine.core.mining.SequenceDb;
import org.maxicp.search.DFSearch;
import org.maxicp.search.PatternSearches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class PrefixAutomatonTest extends CPSolverTest {

    /**
     * Accepts the non-empty words starting with the given symbol.
     */
    static Dfa startsWith(int nSymbols, int symbol) {
        int[][] transitions = new int[2][nSymbols];
        for (int a = 1; a < nSymbols; a++) {
            transitions[0][a] = a == symbol ? 1 : Dfa.DEAD;
            transitions[1][a] = 1;
        }
        return new Dfa(2, 0, transitions, 1);
    }

    /**
     * Accepts the words containing the given symbol.
     */
    static Dfa contains(int nSymbols, int symbol) {
        int[][] transitions = new int[2][nSymbols];
        for (int a = 1; a < nSymbols; a++) {
            transitions[0][a] = a == symbol ? 1 : 0;
            transitions[1][a] = 1;
        }
        return new Dfa(2, 0, transitions, 1);
    }

    /**
     * Accepts the words ending with the given symbol.
     */
    static Dfa endsWith(int nSymbols, int symbol) {
        int[][] transitions = new int[2][nSymbols];
        for (int a = 1; a < nSymbols; a++) {
            transitions[0][a] = a == symbol ? 1 : 0;
            transitions[1][a] = a == symbol ? 1 : 0;
        }
        return new Dfa(2, 0, transitions, 1);
    }

    /**
     * Accepts the words of even length (including the empty word).
     */
    static Dfa evenLength(int nSymbols) {
        int[][] transitions = new int[2][nSymbols];
        for (int a = 1; a < nSymbols; a++) {
            transitions[0][a] = 1;
            transitions[1][a] = 0;
        }
        return new Dfa(2, 0, transitions, 0);
    }

    /**
     * Accepts the words without two consecutive equal symbols; state s > 0 means
     * the last symbol read was s, state 0 is the initial state.
     */
    static Dfa noImmediateRepeat(int nSymbols) {
        int[][] transitions = new int[nSymbols][nSymbols];
        int[] acceptingStates = new int[nSymbols];
        for (int s = 0; s < nSymbols; s++) {
            acceptingStates[s] = s;
            for (int a = 1; a < nSymbols; a++) {
                transitions[s][a] = s == a ? Dfa.DEAD : a;
            }
        }
        return new Dfa(nSymbols, 0, transitions, acceptingStates);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMinStepsToAccepting(CPSolver cp) {
        Dfa dfa = startsWith(4, 2);
        assertEquals(1, dfa.minStepsToAccepting(dfa.initialState()));
        assertEquals(0, dfa.minStepsToAccepting(1));
        assertTrue(dfa.accepts(new int[]{2, 3, 1}, 3));
        assertFalse(dfa.accepts(new int[]{3, 2}, 2));
        assertFalse(dfa.accepts(new int[]{}, 0));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testRootFiltering(CPSolver cp) {
        // sequences over symbols 1..3
        SequenceDb db = new SequenceDb("toy", new int[][]{{1, 2, 3}, {2, 1, 3}, {2, 3, 1}});
        CPSeqPatternVar p = CPFactory.makeSeqPatternVar(cp, db);
        cp.post(new PrefixAutomaton(p, startsWith(db.nSymbols(), 2)));

        // the empty pattern is rejected and only 2 can start the pattern
        assertFalse(p.canTerminate());
        assertFalse(p.isCandidate(1));
        assertTrue(p.isCandidate(2));
        assertFalse(p.isCandidate(3));

        p.append(2);
        cp.fixPoint();
        assertTrue(p.canTerminate()); // "2" is accepted
        assertTrue(p.isCandidate(1) && p.isCandidate(3));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testTerminateInNonAcceptingStateFails(CPSolver cp) {
        SequenceDb db = new SequenceDb("toy", new int[][]{{1, 2}, {1, 2}});
        CPSeqPatternVar p = CPFactory.makeSeqPatternVar(cp, db);
        cp.post(new PrefixAutomaton(p, endsWith(db.nSymbols(), 2)));

        p.append(1);
        cp.fixPoint();
        assertFalse(p.canTerminate()); // "1" does not end with 2
        assertThrowsExactly(InconsistencyException.class, p::terminate);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testUnsatisfiableAutomatonFailsAtPost(CPSolver cp) {
        SequenceDb db = new SequenceDb("toy", new int[][]{{1, 2}});
        CPSeqPatternVar p = CPFactory.makeSeqPatternVar(cp, db);
        // accepting state unreachable: every transition is dead
        int[][] transitions = new int[2][db.nSymbols()];
        for (int a = 1; a < db.nSymbols(); a++) {
            transitions[0][a] = Dfa.DEAD;
            transitions[1][a] = Dfa.DEAD;
        }
        Dfa unsat = new Dfa(2, 0, transitions, 1);
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new PrefixAutomaton(p, unsat)));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBoundedReachabilityUsesRemainingLength(CPSolver cp) {
        // accepting requires reading 1 then 2 then 3, but the longest sequence has length 2
        SequenceDb db = new SequenceDb("toy", new int[][]{{1, 2}, {1, 3}});
        CPSeqPatternVar p = CPFactory.makeSeqPatternVar(cp, db);
        int[][] transitions = new int[4][db.nSymbols()];
        for (int[] row : transitions) {
            java.util.Arrays.fill(row, Dfa.DEAD);
        }
        transitions[0][1] = 1;
        transitions[1][2] = 2;
        transitions[2][3] = 3;
        Dfa needsThree = new Dfa(4, 0, transitions, 3);
        assertEquals(3, needsThree.minStepsToAccepting(0));
        assertThrowsExactly(InconsistencyException.class, () -> cp.post(new PrefixAutomaton(p, needsThree)));
    }

    static Map<String, Integer> collectSolutions(CPSolver cp, CPSeqPatternVar p) {
        DFSearch dfs = CPFactory.makeDfs(cp, PatternSearches.appendExclude(p));
        Map<String, Integer> sols = new HashMap<>();
        int[] buf = new int[p.database().maxSeqLength()];
        dfs.onSolution(() -> {
            int len = p.fillPrefix(buf);
            String key = SeqMiningTestUtils.join(buf, len);
            assertNull(sols.put(key, p.support()), "pattern enumerated twice: " + key);
        });
        dfs.solve();
        return sols;
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testAgainstBruteForceWithSeveralAutomata(Supplier<CPSolver> solverSupplier) {
        for (int seed = 0; seed < 8; seed++) {
            Random rand = new Random(seed);
            int nSeqs = 1 + rand.nextInt(6);
            int nSymbols = 3 + rand.nextInt(3);
            SequenceDb db = SeqMiningTestUtils.randomDb(rand, nSeqs, nSymbols, 4);
            int theta = 1 + rand.nextInt(2);
            int symbol = 1 + rand.nextInt(db.nSymbols() - 1);
            Dfa[] automata = new Dfa[]{
                    startsWith(db.nSymbols(), symbol),
                    contains(db.nSymbols(), symbol),
                    endsWith(db.nSymbols(), symbol),
                    evenLength(db.nSymbols()),
                    noImmediateRepeat(db.nSymbols())};

            for (Dfa dfa : automata) {
                Map<String, Integer> expected = new HashMap<>();
                SeqMiningTestUtils.frequentSequences(db, theta, db.maxSeqLength()).forEach((pattern, support) -> {
                    int[] word = pattern.isEmpty() ? new int[0]
                            : java.util.Arrays.stream(pattern.split(" ")).mapToInt(Integer::parseInt).toArray();
                    if (dfa.accepts(word, word.length))
                        expected.put(pattern, support);
                });

                CPSolver cp = solverSupplier.get();
                CPSeqPatternVar p = CPFactory.makeSeqPatternVar(cp, db);
                try {
                    cp.post(new MinSupportSeq(p, theta));
                    cp.post(new PrefixAutomaton(p, dfa));
                } catch (InconsistencyException e) {
                    assertTrue(expected.isEmpty(), "seed " + seed);
                    continue;
                }
                assertEquals(expected, collectSolutions(cp, p), "seed " + seed);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("solverSupplier")
    public void testCombinedQueryAgainstBruteForce(Supplier<CPSolver> solverSupplier) {
        // combined query: support + regular + length window filter one shared frontier
        for (int seed = 0; seed < 8; seed++) {
            Random rand = new Random(seed + 500);
            int nSeqs = 2 + rand.nextInt(5);
            int nSymbols = 3 + rand.nextInt(3);
            SequenceDb db = SeqMiningTestUtils.randomDb(rand, nSeqs, nSymbols, 5);
            int theta = 1 + rand.nextInt(2);
            int symbol = 1 + rand.nextInt(db.nSymbols() - 1);
            int minLength = 1 + rand.nextInt(2);
            int maxLength = minLength + 1 + rand.nextInt(2);
            Dfa dfa = contains(db.nSymbols(), symbol);

            Map<String, Integer> expected = new HashMap<>();
            SeqMiningTestUtils.frequentSequences(db, theta, db.maxSeqLength()).forEach((pattern, support) -> {
                int[] word = pattern.isEmpty() ? new int[0]
                        : java.util.Arrays.stream(pattern.split(" ")).mapToInt(Integer::parseInt).toArray();
                if (word.length >= minLength && word.length <= maxLength && dfa.accepts(word, word.length))
                    expected.put(pattern, support);
            });

            CPSolver cp = solverSupplier.get();
            CPSeqPatternVar p = CPFactory.makeSeqPatternVar(cp, db);
            String ctx = "seed " + seed + " symbol " + symbol + " window [" + minLength + "," + maxLength + "]";
            try {
                cp.post(new MinSupportSeq(p, theta));
                cp.post(new PrefixAutomaton(p, dfa));
                cp.post(new PatternLengthSeq(p, minLength, maxLength));
            } catch (InconsistencyException e) {
                assertTrue(expected.isEmpty(), ctx);
                continue;
            }
            assertEquals(expected, collectSolutions(cp, p), ctx);
        }
    }
}
