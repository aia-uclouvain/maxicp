/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.copy;

import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;

/**
 * Implementation of {@link StateInt} with copy strategy
 * @see Copier
 * @see StateManager#makeStateInt(int)
 */
public class CopyInt extends Copy<Integer> implements StateInt {

    protected CopyInt(int initial) {
        super(initial);
    }

}
