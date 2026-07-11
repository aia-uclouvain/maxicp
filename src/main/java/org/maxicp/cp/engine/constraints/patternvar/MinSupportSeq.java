/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.patternvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPSeqPatternVar;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Minimum support constraint on a sequence pattern variable:
 * the pattern must be contained in at least theta database sequences.
 * <p>
 * The filtering is exact for the immediate extensions of the current prefix:
 * by anti-monotonicity of the support, a candidate whose projected support is
 * below the threshold can never lead to a frequent pattern and is excluded.
 */
public class MinSupportSeq extends AbstractCPConstraint {

    private final CPSeqPatternVar p;
    private final int theta;
    private final int[] candidates;

    /**
     * Creates a minimum support constraint.
     *
     * @param p     the sequence pattern variable
     * @param theta the minimum support, at least 1
     */
    public MinSupportSeq(CPSeqPatternVar p, int theta) {
        super(p.getSolver());
        if (theta < 1)
            throw new IllegalArgumentException("theta must be >= 1: " + theta);
        this.p = p;
        this.theta = theta;
        this.candidates = new int[Math.max(p.nSymbols() - 1, 1)];
    }

    @Override
    public void post() {
        p.propagateOnDomainChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        if (p.support() < theta)
            throw new InconsistencyException();
        int n = p.fillCandidates(candidates);
        for (int i = 0; i < n; i++)
            if (p.supportAfter(candidates[i]) < theta)
                p.excludeNext(candidates[i]);
    }
}
