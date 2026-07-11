/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core.mining;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Deterministic finite automaton over pattern symbols, used to state
 * regular-language restrictions on sequence patterns.
 * <p>
 * Symbols follow the mining convention: valid symbols are {@code 1..nSymbols()-1}
 * (0 is the reserved epsilon and never read by the automaton). A missing
 * transition is represented by {@link #DEAD} and means immediate rejection.
 */
public class Dfa {

    /**
     * Transition target meaning rejection of the word.
     */
    public static final int DEAD = -1;

    private final int nStates;
    private final int initialState;
    private final boolean[] accepting;
    private final int[][] transitions;
    private final int[] minStepsToAccepting;

    /**
     * Creates a deterministic finite automaton.
     *
     * @param nStates         the number of states, identified by {@code 0..nStates-1}
     * @param initialState    the initial state
     * @param transitions     the transition table: {@code transitions[s][a]} is the
     *                        state reached from state s on symbol a, or {@link #DEAD};
     *                        column 0 (epsilon) is ignored
     * @param acceptingStates the accepting states
     */
    public Dfa(int nStates, int initialState, int[][] transitions, int... acceptingStates) {
        if (transitions.length != nStates)
            throw new IllegalArgumentException("one transition row per state is required");
        if (initialState < 0 || initialState >= nStates)
            throw new IllegalArgumentException("invalid initial state " + initialState);
        this.nStates = nStates;
        this.initialState = initialState;
        this.transitions = new int[nStates][];
        for (int s = 0; s < nStates; s++) {
            this.transitions[s] = transitions[s].clone();
            for (int target : transitions[s]) {
                if (target < DEAD || target >= nStates)
                    throw new IllegalArgumentException("invalid transition target " + target);
            }
        }
        this.accepting = new boolean[nStates];
        for (int s : acceptingStates) {
            accepting[s] = true;
        }
        this.minStepsToAccepting = computeMinSteps();
    }

    /**
     * Multi-source reverse BFS from the accepting states.
     */
    private int[] computeMinSteps() {
        List<List<Integer>> reverse = new ArrayList<>(nStates);
        for (int s = 0; s < nStates; s++) {
            reverse.add(new ArrayList<>());
        }
        for (int s = 0; s < nStates; s++) {
            for (int a = 1; a < transitions[s].length; a++) {
                if (transitions[s][a] != DEAD)
                    reverse.get(transitions[s][a]).add(s);
            }
        }
        int[] dist = new int[nStates];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Deque<Integer> queue = new ArrayDeque<>();
        for (int s = 0; s < nStates; s++) {
            if (accepting[s]) {
                dist[s] = 0;
                queue.add(s);
            }
        }
        while (!queue.isEmpty()) {
            int s = queue.poll();
            for (int pred : reverse.get(s)) {
                if (dist[pred] == Integer.MAX_VALUE) {
                    dist[pred] = dist[s] + 1;
                    queue.add(pred);
                }
            }
        }
        return dist;
    }

    /**
     * Returns the number of states.
     *
     * @return the number of states
     */
    public int nStates() {
        return nStates;
    }

    /**
     * Returns the initial state.
     *
     * @return the initial state
     */
    public int initialState() {
        return initialState;
    }

    /**
     * Returns the state reached from a state on a symbol.
     *
     * @param state the source state
     * @param symbol the symbol read, with {@code 1 <= symbol}
     * @return the target state, or {@link #DEAD} if the word is rejected
     */
    public int next(int state, int symbol) {
        return symbol < transitions[state].length ? transitions[state][symbol] : DEAD;
    }

    /**
     * Returns if a state is accepting.
     *
     * @param state the state
     * @return true if the state is accepting
     */
    public boolean isAccepting(int state) {
        return accepting[state];
    }

    /**
     * Returns the minimum number of symbols to read from a state to reach an
     * accepting state.
     *
     * @param state the state
     * @return the minimum word length to acceptance, or {@code Integer.MAX_VALUE}
     * if no accepting state is reachable
     */
    public int minStepsToAccepting(int state) {
        return minStepsToAccepting[state];
    }

    /**
     * Returns if the automaton accepts a word.
     *
     * @param word the symbols of the word
     * @param n    the number of symbols to read
     * @return true if the word is accepted
     */
    public boolean accepts(int[] word, int n) {
        int state = initialState;
        for (int k = 0; k < n; k++) {
            state = next(state, word[k]);
            if (state == DEAD)
                return false;
        }
        return accepting[state];
    }
}
