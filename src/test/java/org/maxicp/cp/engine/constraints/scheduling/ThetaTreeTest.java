/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.scheduling;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThetaTreeTest {

    @Test
    public void simpleTest0() {
        ThetaTree tree = new ThetaTree(4);
        tree.insert(0, 0, 5);
        assertEquals(5, tree.getEct());
        tree.insert(1, 25, 6);
        assertEquals(31, tree.getEct());
        tree.insert(2, 30, 4);
        assertEquals(35, tree.getEct());
        tree.insert(3, 32, 10);
        assertEquals(45, tree.getEct());
        tree.remove(3);
        assertEquals(35, tree.getEct());
        tree.reset();
        assertEquals(Integer.MIN_VALUE, tree.getEct());
    }

}
