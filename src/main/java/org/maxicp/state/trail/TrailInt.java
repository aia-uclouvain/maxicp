/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.trail;


import org.maxicp.state.StateEntry;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;

/**
 * Implementation of {@link StateInt} with trail strategy.
 * The value is stored as a primitive to avoid boxing and
 * to compare values rather than {@link Integer} references.
 * @see Trailer
 * @see StateManager#makeStateInt(int)
 */
public class TrailInt implements StateInt {

    class TrailIntStateEntry implements StateEntry {
        private final int v;

        TrailIntStateEntry(int v) {
            this.v = v;
        }

        @Override
        public void restore() {
            TrailInt.this.v = v;
        }
    }

    private final Trailer trail;
    private int v;
    private long lastMagic;

    protected TrailInt(Trailer trail, int initial) {
        this.trail = trail;
        v = initial;
        lastMagic = trail.getMagic();
    }

    private void trail() {
        long trailMagic = trail.getMagic();
        if (lastMagic != trailMagic) {
            lastMagic = trailMagic;
            trail.pushState(new TrailIntStateEntry(v));
        }
    }

    @Override
    public Integer setValue(Integer v) {
        int newValue = v;
        if (newValue != this.v) {
            trail();
            this.v = newValue;
        }
        return this.v;
    }

    @Override
    public Integer value() {
        return v;
    }

    @Override
    public String toString() {
        return "" + v;
    }
}
