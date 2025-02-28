/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;

import java.util.Map;

/**
 * A generic map that can revert its state
 * with {@link StateManager#saveState()} / {@link StateManager#restoreState()}
 * methods.
 *
 * @param <K> the key type
 * @param <V> the value type
 * @see StateManager#makeStateMap() for the creation.
 */
public interface StateMap<K, V> extends Map<K,V> {

}
