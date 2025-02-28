/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;

/**
 * A StateEntry is aimed to be
 * stored by a StateManager to revert some state
 */
public interface StateEntry {
    void restore();
}
