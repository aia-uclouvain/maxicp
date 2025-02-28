/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;

/**
 * Object that wraps a reference
 * and can be saved and restored through
 * the {@link StateManager#saveState()} / {@link StateManager#restoreState()}
 * methods.
 *
 * @see StateManager#makeStateRef(Object)  for the creation.
 * @param <T> the type of the value wrapped in this state
 */
public interface State<T> {

    /**
     * Set the value
     * @param v the value to set
     * @return the new value that was set
     */
    T setValue(T v);

    /**
     * Retrieves the value
     * @return the value
     */
    T value();


    @Override
    String toString();
}
