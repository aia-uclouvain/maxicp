/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;


/**
 * Object that wraps an integer value
 * that can be saved and restored through
 * the {@link StateManager#saveState()} / {@link StateManager#restoreState()}
 * methods.
 *
 * @see StateManager#makeStateInt(int) for the creation.
 */
public interface StateInt extends State<Integer> {

    /**
     * Increments the value
     * @return the new value
     */
    default int increment() {
        return setValue(value() + 1);
    }

    /**
     * Decrements the value
     * @return the new value
     */
    default int decrement() {
        return setValue(value() - 1);
    }

}
