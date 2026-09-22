package org.maxicp.search.blackbox;

import java.util.Random;
import java.util.function.Supplier;

/**
 * Decorates a branching supplier and randomly swaps binary branches.
 */
final class RandomizedBranching implements Supplier<Runnable[]> {

    private final Supplier<Runnable[]> delegate;
    private final Random random;
    private final double swapProbability;

    RandomizedBranching(Supplier<Runnable[]> delegate, Random random, double swapProbability) {
        this.delegate = delegate;
        this.random = random;
        this.swapProbability = swapProbability;
    }

    @Override
    public Runnable[] get() {
        Runnable[] branches = delegate.get();
        if (branches.length == 2 && random.nextDouble() < swapProbability) {
            Runnable tmp = branches[0];
            branches[0] = branches[1];
            branches[1] = tmp;
        }
        return branches;
    }
}

