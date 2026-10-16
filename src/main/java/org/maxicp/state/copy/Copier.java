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

    /**
     * Adds a new element to the store. An element created after a save is not in that save's copy, so restoring the
     * level only dropped it from the store and it kept the value it was given at that level. Its initial value is
     * added to the copy of the current level, so that restoring the level restores it too.
     * 
     * Real life use case: a constraint creates dynamically a Trail during a fixpoint, that will be use *above* 
     * the current search tree node. It is then propagated again in the same fixpoint. If the initial value 
     * is not always preserved, then when backtracking the constraint will have an incoherent state and no way to recover.
     */
    private <S extends Storage> S register(S s) {
        store.add(s);
        if (!prior.isEmpty())
            prior.peek().add(s.save());
        return s;
    }

    @Override
    public <T> State<T> makeStateRef(T initValue) {
        return register(new Copy<>(initValue));
    }

    @Override
    public StateInt makeStateInt(int initValue) {
        return register(new CopyInt(initValue));
    }

    @Override
    public StateLong makeStateLong(long initValue) {
        return register(new CopyLong(initValue));
    }

    @Override
    public <K,V> StateMap<K,V> makeStateMap() {
        return register(new CopyMap<K, V>());
    }

    @Override
    public String toString() {
        return "Copier";
    }

}
