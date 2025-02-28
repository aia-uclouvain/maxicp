/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;

import java.util.function.Supplier;

import static org.maxicp.search.Searches.EMPTY;

public class LimitedDepthBranching implements Supplier<Runnable[]> {

    private int curD;
    private final int maxD;
    private final Supplier<Runnable[]> bs;

    /**
     * Creates a depth-limited combinator on a given branching.
     *
     * @param branching the branching on which to apply the depth-limiting combinator
     * @param maxDepth the maximum depth limit. Any node exceeding that limit is pruned.
     */
    public LimitedDepthBranching(Supplier<Runnable[]> branching, int maxDepth) {
        if (maxDepth < 0) throw new IllegalArgumentException("max depth should be >= 0");
        this.bs = branching;
        this.maxD = maxDepth;
    }

    @Override
    public Runnable[] get() {
        if(curD == maxD)
            return EMPTY;

        Runnable[] branches = bs.get();

        Runnable[] newBranches = new Runnable[branches.length];
        for (int i = 0; i < newBranches.length; i++) {
            int fi = i;
            int d = curD + 1; // branch index
            newBranches[i] = () -> {
                curD = d; // update depth
                branches[fi].run();
            };
        }

        return newBranches;
    }
}