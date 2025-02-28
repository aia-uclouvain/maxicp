/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.trail;


import org.maxicp.state.*;
import org.maxicp.state.copy.Copier;

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/**
 * StateManager that will lazily store
 * the state of state object
 * at each {@link #saveState()} call.
 * Only the one that effectively change are stored
 * and at most once between any to call to {@link #saveState()}.
 * This can be seen as an optimized version of {@link Copier}.
 */
public class Trailer implements StateManager {

    static class Backup extends Stack<StateEntry> {
        Backup() {
        }

        void restore() {
            while (!isEmpty())
                pop().restore();
        }
    }

    private Stack<Backup> prior;
    private Backup current;
    private long magic = 0L;

    private List<Runnable> onRestoreListeners;

    public Trailer() {
        prior = new Stack<Backup>();
        current = new Backup();
        onRestoreListeners = new LinkedList<Runnable>();
    }

    private void notifyRestore() {
        for (Runnable l : onRestoreListeners) {
            l.run();
        }
    }

    @Override
    public void onRestore(Runnable listener) {
        onRestoreListeners.add(listener);
    }

    public long getMagic() {
        return magic;
    }

    public void pushState(StateEntry entry) {
        current.push(entry);
    }

    @Override
    public int getLevel() {
        return prior.size() - 1;
    }

    @Override
    public void saveState() {
        prior.add(current);
        current = new Backup();
        magic++;
    }


    @Override
    public void restoreState() {
        current.restore();
        current = prior.pop();
        magic++;
        notifyRestore();
    }

    @Override
    public void restoreStateUntil(int level) {
        while (getLevel() > level)
            restoreState();
    }

    @Override
    public <T> State<T> makeStateRef(T initValue) {
        return new Trail<>(this,initValue);
    }

    @Override
    public StateInt makeStateInt(int initValue) {
        return new TrailInt(this,initValue);
    }

    @Override
    public StateLong makeStateLong(long initValue) {
        return new TrailLong(this,initValue);
    }

    @Override
    public <K, V> StateMap<K, V> makeStateMap() {
        return new TrailMap<K,V>(this);
    }

    @Override
    public String toString() {
        return "Trailer";
    }
}
