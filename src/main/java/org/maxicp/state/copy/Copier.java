/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.copy;

import org.maxicp.state.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/**
 * StateManager that will store
 * the state of every created elements
 * at each {@link #saveState()} call.
 */
public class Copier implements StateManager {

    class Backup extends Stack<StateEntry> {
        private int sz;

        Backup() {
            sz = store.size();
            for (Storage s : store)
                add(s.save());
        }

        void restore() {
            store.setSize(sz);
            for (StateEntry se : this)
                se.restore();
        }
    }

    private Stack<Storage> store;
    private Stack<Backup> prior;
    private List<Runnable> onRestoreListeners;

    public Copier() {
        store = new Stack<Storage>();
        prior = new Stack<Backup>();
        onRestoreListeners = new LinkedList<Runnable>();
    }

    private void notifyRestore() {
        for (Runnable l: onRestoreListeners) {
            l.run();
        }
    }

    @Override
    public void onRestore(Runnable listener) {
        onRestoreListeners.add(listener);
    }

    public int getLevel() {
        return prior.size() - 1;
    }


    public int storeSize() {
        return store.size();
    }

    @Override
    public void saveState() {
        prior.add(new Backup());
    }

    @Override
    public void restoreState() {
        prior.pop().restore();
        notifyRestore();
    }

    @Override
    public void restoreStateUntil(int level) {
        while (getLevel() > level)
            restoreState();
    }

    @Override
    public <T> State<T> makeStateRef(T initValue) {
        Copy r = new Copy(initValue);
        store.add(r);
        return r;
    }

    @Override
    public StateInt makeStateInt(int initValue) {
        CopyInt s = new CopyInt(initValue);
        store.add(s);
        return s;
    }

    @Override
    public StateLong makeStateLong(long initValue) {
        CopyLong s = new CopyLong(initValue);
        store.add(s);
        return s;
    }

    @Override
    public <K,V> StateMap<K,V> makeStateMap() {
        CopyMap<K, V> s = new CopyMap<>();
        store.add(s);
        return s;
    }

    @Override
    public String toString() {
        return "Copier";
    }

}
