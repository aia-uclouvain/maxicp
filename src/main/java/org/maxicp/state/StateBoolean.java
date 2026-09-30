/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;


/**
 * Object that wraps a boolean value
 * that can be saved and restored through
 * the {@link StateManager#saveState()} / {@link StateManager#restoreState()}
 * methods.
 *
 * @see StateManager#makeStateBoolean(boolean) for the creation.
 */
public interface StateBoolean extends State<Boolean> {

}
