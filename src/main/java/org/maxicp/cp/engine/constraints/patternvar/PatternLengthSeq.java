/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPSeqPatternVar;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Length window constraint on a sequence pattern variable:
 * the final pattern length must lie in {@code [minLength, maxLength]}.
 * <p>
 * While the prefix is shorter than the minimum length, termination is forbidden
 * (the restriction applies to the current prefix only and is re-established on
 * each propagation, so it remains sound across appends and backtracks).
 * When the prefix reaches the maximum length, all candidates are excluded,
 * which auto-terminates the variable.
 */
public class PatternLengthSeq extends AbstractCPConstraint {

    private final CPSeqPatternVar p;
    private final int minLength;
    private final int maxLength;
    private final int[] candidates;

    /**
     * Creates a length window constraint.
     *
     * @param p         the sequence pattern variable
     * @param minLength the minimum pattern length, non negative
     * @param maxLength the maximum pattern length, at least minLength
     */
    public PatternLengthSeq(CPSeqPatternVar p, int minLength, int maxLength) {
        super(p.getSolver());
        if (minLength < 0 || maxLength < minLength)
            throw new IllegalArgumentException("invalid length window [" + minLength + "," + maxLength + "]");
        this.p = p;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.candidates = new int[Math.max(p.nSymbols() - 1, 1)];
    }

    @Override
    public void post() {
        p.propagateOnDomainChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        int len = p.prefixLength();
        if (len > maxLength)
            throw new InconsistencyException();
        if (len < minLength) {
            if (p.isTerminated())
                throw new InconsistencyException();
            p.forbidTerminate();
        } else if (len == maxLength && !p.isTerminated()) {
            // no extension allowed: excluding every candidate auto-terminates the variable
            int n = p.fillCandidates(candidates);
            for (int i = 0; i < n; i++)
                p.excludeNext(candidates[i]);
        }
    }
}
