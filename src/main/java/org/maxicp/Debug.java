/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp;

import org.maxicp.state.State;
import org.maxicp.state.StateManager;
import org.maxicp.state.trail.Trailer;

public class Debug {
    public static void main(String[] args) {

        StateManager sm = new Trailer();

        State<Boolean> a = sm.makeStateRef(true);
        // level 0, a is true

        sm.saveState(); // level 1, a is true recorded
        sm.saveState(); // level 2, a is true recorded

        a.setValue(false);

        sm.restoreState(); // level 1, a is true

        a.setValue(false); // level 1, a is false

        sm.saveState(); // level 2, a is false recorded

        sm.restoreState(); // level 1 a is false
        sm.restoreState(); // level 0 a is true

        System.out.println(a.value() +" should be true");

    }

}
