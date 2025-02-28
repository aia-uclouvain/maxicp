/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.trail;


import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;

/**
 * Implementation of {@link StateInt} with trail strategy
 * @see Trailer
 * @see StateManager#makeStateInt(int)
 */
public class TrailInt extends Trail<Integer> implements StateInt {

    protected TrailInt(Trailer trail, int initial) {
        super(trail, initial);
    }

}
