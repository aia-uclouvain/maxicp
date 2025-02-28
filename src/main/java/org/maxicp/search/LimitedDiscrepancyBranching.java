/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;

import java.util.function.Supplier;

import static org.maxicp.search.Searches.EMPTY;

/**
 * Branching combinator
 * that ensures that that the alternatives created are always within the
 * discrepancy limit.
 * The discrepancy of an alternative generated
 * for a given node is the distance from the left most alternative.
 * The discrepancy of a node is the sum of the discrepancy of its ancestors.
 */
public class LimitedDiscrepancyBranching implements Supplier<Runnable[]> {

    private int curD;
    private final int maxD;
    private final Supplier<Runnable[]> bs;

    /**
     * Creates a discprepancy combinator on a given branching.
     *
     * @param branching the branching on which to apply the discrepancy combinator
     * @param maxDiscrepancy the maximum discrepancy limit. Any node exceeding
     *                       that limit is pruned.
     */
    public LimitedDiscrepancyBranching(Supplier<Runnable[]> branching, int maxDiscrepancy) {
        if (maxDiscrepancy < 0) throw new IllegalArgumentException("max discrepancy should be >= 0");
        this.bs = branching;
        this.maxD = maxDiscrepancy;
    }

    @Override
    public Runnable[] get() {
        Runnable[] branches = bs.get();

        int k = Math.min(maxD - curD + 1, branches.length);

        if (k == 0) return EMPTY;

        Runnable[] kFirstBranches = new Runnable[k];
        for (int i = 0; i < k; i++) {
            int bi = i;
            int d = curD + bi; // branch index
            kFirstBranches[i] = () -> {
                curD = d; // update discrepancy
                branches[bi].run();
            };
        }

        return kFirstBranches;
    }
}
