/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPSeqPatternVar;
import org.maxicp.cp.engine.core.mining.Dfa;
import org.maxicp.state.StateInt;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Regular-language restriction on a sequence pattern variable: the complete
 * pattern must be accepted by a deterministic finite automaton.
 * <p>
 * The propagator maintains the automaton state reached by the committed prefix
 * (advanced incrementally and restored on backtrack), forbids termination while
 * that state is not accepting, and removes every candidate symbol whose successor
 * state cannot reach an accepting state within the remaining pattern length.
 * This filters the refinement frontier directly, without materializing
 * positional variables, and establishes extension consistency for the
 * automaton restriction.
 */
public class PrefixAutomaton extends AbstractCPConstraint {

    private final CPSeqPatternVar pattern;
    private final Dfa dfa;
    private final StateInt state;
    private final StateInt processed;
    private final int[] candidates;

    /**
     * Creates a regular-language constraint on a sequence pattern.
     *
     * @param pattern the sequence pattern variable
     * @param dfa     the automaton accepting the allowed patterns; it must define
     *                its transitions over the symbols of the pattern alphabet
     */
    public PrefixAutomaton(CPSeqPatternVar pattern, Dfa dfa) {
        super(pattern.getSolver());
        this.pattern = pattern;
        this.dfa = dfa;
        this.state = getSolver().getStateManager().makeStateInt(dfa.initialState());
        this.processed = getSolver().getStateManager().makeStateInt(0);
        this.candidates = new int[pattern.nSymbols()];
    }

    @Override
    public void post() {
        pattern.propagateOnDomainChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        // advance the automaton along the newly committed prefix symbols
        int current = state.value();
        int length = pattern.prefixLength();
        for (int j = processed.value(); j < length; j++) {
            current = dfa.next(current, pattern.prefixSymbol(j));
            if (current == Dfa.DEAD) {
                throw new InconsistencyException();
            }
        }
        state.setValue(current);
        processed.setValue(length);

        // patterns never exceed the longest database sequence
        int remaining = pattern.database().maxSeqLength() - length;
        if (dfa.minStepsToAccepting(current) > remaining) {
            throw new InconsistencyException();
        }
        if (pattern.isTerminated()) {
            if (!dfa.isAccepting(current)) {
                throw new InconsistencyException();
            }
            return;
        }
        if (!dfa.isAccepting(current)) {
            pattern.forbidTerminate();
        }
        int n = pattern.fillCandidates(candidates);
        for (int k = 0; k < n; k++) {
            int next = dfa.next(current, candidates[k]);
            if (next == Dfa.DEAD || dfa.minStepsToAccepting(next) > remaining - 1) {
                pattern.excludeNext(candidates[k]);
            }
        }
    }
}
