/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state;

import org.maxicp.state.copy.Copier;
import org.maxicp.state.trail.Trailer;
import java.util.stream.Stream;

public abstract class StateManagerTest {

    public static Stream<StateManager> getStateManager() {
        return Stream.of(new Trailer(), new Copier());
    }
}
