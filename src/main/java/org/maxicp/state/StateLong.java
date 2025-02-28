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
public interface StateLong extends State<Long> {

    /**
     * Increments the value
     * @return the new value
     */
    default long increment() {
        return setValue(value() + 1);
    }

    /**
     * Decrements the value
     * @return the new value
     */
    default long decrement() {
        return setValue(value() - 1);
    }

}
