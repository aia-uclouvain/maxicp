/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.search.IntObjective;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maximization objective function
 */
public class Maximize implements IntObjective {
    private final AtomicInteger bound;

    private int delta = 1;
    private final CPIntVar x;

    private boolean filter = true;

    public Maximize(CPIntVar x, AtomicInteger bound) {
        this.x = x;
        this.bound = bound;
    }

    public Maximize(CPIntVar x) {
        this(x, new AtomicInteger(Integer.MIN_VALUE+1));
    }

    @Override
    public void setFilter(boolean activate) {
        this.filter = activate;
    }

    @Override
    public void setDelta(int delta) {
        if (delta < 0) throw new IllegalArgumentException("delta should be >= 0");
        this.delta = delta;
    }

    @Override
    public void tighten() {
        if (!x.isFixed()) throw new RuntimeException("objective not bound");
        int newValue = x.max() + delta;
        bound.getAndUpdate((value) -> Math.max(newValue, value));
    }

    @Override
    public void relax() {
        bound.set(Integer.MIN_VALUE);
    }

    @Override
    public void setBound(int newBound) {
        bound.set(newBound);
    }

    @Override
    public int getBound() {
        return bound.get();
    }

    @Override
    public void filter() {
        if (filter) {
            x.removeBelow(bound.get());
        }
    }
}
