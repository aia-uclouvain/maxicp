/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.trail;

import org.maxicp.state.State;
import org.maxicp.state.StateEntry;
import org.maxicp.state.StateManager;

/**
 * Implementation of {@link State} with trail strategy
 * 
 * @see Trailer
 * @see StateManager#makeStateRef(Object)
 * @param <T> the type of the value wrapped in this state
 */
public class Trail<T> implements State<T> {

    class TrailStateEntry implements StateEntry {
        private final T v;

        TrailStateEntry(T v) {
            this.v = v;
        }

        @Override
        public void restore() {
            Trail.this.v = v;
        }
    }

    private Trailer trail;
    private T v;
    private long lastMagic = -1L;

    protected Trail(Trailer trail, T initial) {
        this.trail = trail;
        v = initial;
        // Do not use the current magic. The first change must always backup the initi
        // l value.
        // Real life use case: a constraint creates dynamically a Trail during a fixpoint, that will be use *above* the current search tree node. It is then propagated again in the same fixpoint. If the initial value is not always preserved, then when backtracking the constraint will have an incoherent state and no way to recover.
        lastMagic = trail.getMagic() - 1;
    }

    private void trail() {
        long trailMagic = trail.getMagic();
        if (lastMagic != trailMagic) {
            lastMagic = trailMagic;
            trail.pushState(new TrailStateEntry(v));
        }
    }

    @Override
    public T setValue(T v) {
        if (v != this.v) {
            trail();
            this.v = v;
        }
        return this.v;
    }

    @Override
    public T value() {
        return this.v;
    }

    @Override
    public String toString() {
        return "" + v;
    }
}
