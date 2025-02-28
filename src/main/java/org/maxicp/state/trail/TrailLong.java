/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.trail;


import org.maxicp.state.StateInt;
import org.maxicp.state.StateLong;
import org.maxicp.state.StateManager;

/**
 * Implementation of {@link StateInt} with trail strategy
 * @see Trailer
 * @see StateManager#makeStateInt(int)
 */
public class TrailLong extends Trail<Long> implements StateLong {

    protected TrailLong(Trailer trail, long initial) {
        super(trail, initial);
    }

}
