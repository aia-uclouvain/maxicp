/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.copy;

import org.maxicp.state.State;
import org.maxicp.state.StateEntry;
import org.maxicp.state.StateManager;
import org.maxicp.state.Storage;

/**
 * Implementation of {@link State} with copy strategy
 * @see Copier
 * @see StateManager#makeStateRef(Object)
 */
public class Copy<T> implements Storage, State<T> {

    class CopyStateEntry implements StateEntry {
        private final T v;

        CopyStateEntry(T v) {
            this.v = v;
        }
        @Override public void restore() {
            Copy.this.v = v;
        }
    }

    private T v;

    protected Copy(T initial) {
        v = initial;
    }

    @Override
    public T setValue(T v) {
        this.v = v;
        return v;
    }

    @Override
    public T value() {
        return v;
    }


    @Override
    public String toString() {
        return String.valueOf(v);
    }

    @Override
    public StateEntry save() {
        return new CopyStateEntry(v);
    }
}
