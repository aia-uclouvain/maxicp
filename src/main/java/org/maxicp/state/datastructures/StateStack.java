/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.datastructures;

import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;

import java.util.ArrayList;

/**
 * Generic Stack that can be saved and restored through
 * the {@link StateManager#saveState()} / {@link StateManager#restoreState()}
 * methods.
 * @param <E> the type of the elements in the stack
 */
public class StateStack<E> {

    private StateInt size;
    private ArrayList<E> stack;

    /**
     * Creates a restorable stack.
     * @param sm the state manager that saves/restores the stack
     *         when {@link StateManager#saveState()} / {@link StateManager#restoreState()}
     *         methods are called.
     */
    public StateStack(StateManager sm) {
        size = sm.makeStateInt(0);
        stack = new ArrayList<E>();
    }

    public void push(E elem) {
        int s = size.value();
        if (stack.size() > s) stack.set(s, elem);
        else stack.add(elem);
        size.increment();
    }

    public int size() {
        return size.value();
    }

    public E get(int index) {
        return stack.get(index);
    }
}
