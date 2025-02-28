/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.copy;

import org.maxicp.state.StateLong;
import org.maxicp.state.StateManager;

/**
 * Implementation of {@link StateLong} with copy strategy
 * @see Copier
 * @see StateManager#makeStateLong(long)
 */
public class CopyLong extends Copy<Long> implements StateLong {

    protected CopyLong(long initial) {
        super(initial);
    }

}
