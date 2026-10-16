/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.trail;


import org.maxicp.state.StateEntry;
import org.maxicp.state.StateLong;
import org.maxicp.state.StateManager;

/**
 * Implementation of {@link StateLong} with trail strategy.
 * The value is stored as a primitive to avoid boxing and
 * to compare values rather than {@link Long} references.
 * @see Trailer
 * @see StateManager#makeStateLong(long)
 */
public class TrailLong implements StateLong {

    class TrailLongStateEntry implements StateEntry {
        private final long v;

        TrailLongStateEntry(long v) {
            this.v = v;
        }

        @Override
        public void restore() {
            TrailLong.this.v = v;
        }
    }

    private final Trailer trail;
    private long v;
    private long lastMagic;

    protected TrailLong(Trailer trail, long initial) {
        this.trail = trail;
        v = initial;
        lastMagic = trail.getMagic();
    }

    private void trail() {
        long trailMagic = trail.getMagic();
        if (lastMagic != trailMagic) {
            lastMagic = trailMagic;
            trail.pushState(new TrailLongStateEntry(v));
        }
    }

    @Override
    public Long setValue(Long v) {
        long newValue = v;
        if (newValue != this.v) {
            trail();
            this.v = newValue;
        }
        return this.v;
    }

    @Override
    public Long value() {
        return v;
    }

    @Override
    public String toString() {
        return "" + v;
    }
}
