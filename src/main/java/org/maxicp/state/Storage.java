/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;

import org.maxicp.state.copy.Copier;

/**
 * Object that can be saved by the {@link Copier}.
 */
public interface Storage {
    StateEntry save();
}
