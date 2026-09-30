/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.copy;

import org.maxicp.state.StateBoolean;
import org.maxicp.state.StateManager;

/**
 * Implementation of {@link StateBoolean} with copy strategy
 * @see Copier
 * @see StateManager#makeStateBoolean(boolean)
 */
public class CopyBoolean extends Copy<Boolean> implements StateBoolean {

    protected CopyBoolean(boolean initial) {
        super(initial);
    }

}
