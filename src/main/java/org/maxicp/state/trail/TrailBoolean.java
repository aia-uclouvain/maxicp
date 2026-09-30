/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.trail;


import org.maxicp.state.StateBoolean;
import org.maxicp.state.StateEntry;
import org.maxicp.state.StateManager;

/**
 * Implementation of {@link StateBoolean} with trail strategy.
 * The value is stored as a primitive to avoid boxing.
 * @see Trailer
 * @see StateManager#makeStateBoolean(boolean)
 */
public class TrailBoolean implements StateBoolean {

    class TrailBooleanStateEntry implements StateEntry {
        private final boolean v;

        TrailBooleanStateEntry(boolean v) {
            this.v = v;
        }

        @Override
        public void restore() {
            TrailBoolean.this.v = v;
        }
    }

    private final Trailer trail;
    private boolean v;
    private long lastMagic;

    protected TrailBoolean(Trailer trail, boolean initial) {
        this.trail = trail;
        v = initial;
        lastMagic = trail.getMagic();
    }

    private void trail() {
        long trailMagic = trail.getMagic();
        if (lastMagic != trailMagic) {
            lastMagic = trailMagic;
            trail.pushState(new TrailBooleanStateEntry(v));
        }
    }

    @Override
    public Boolean setValue(Boolean v) {
        boolean newValue = v;
        if (newValue != this.v) {
            trail();
            this.v = newValue;
        }
        return this.v;
    }

    @Override
    public Boolean value() {
        return v;
    }

    @Override
    public String toString() {
        return "" + v;
    }
}
