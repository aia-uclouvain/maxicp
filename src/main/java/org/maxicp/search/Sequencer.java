/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;


import java.util.function.Supplier;

import static org.maxicp.search.Searches.EMPTY;

/**
 * Sequential Search combinator that linearly
 * considers a list of branching generator.
 * One branching of this list is executed
 * when all the previous ones are exhausted, that is
 * they return an empty array.
 */
public class Sequencer implements Supplier<Runnable[]> {
    private Supplier<Runnable[]>[] branching;

    /**
     * Creates a sequential search combinator.
     *
     * @param branching the sequence of branching
     */
    public Sequencer(Supplier<Runnable[]>... branching) {
        this.branching = branching;
    }

    @Override
    public Runnable[] get() {
        for (int i = 0; i < branching.length; i++) {
            Runnable[] alts = branching[i].get();
            if (alts.length != 0)
                return alts;
        }
        return EMPTY;
    }
}
