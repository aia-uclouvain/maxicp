/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.utils;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThetaTreeTest {

    @Test
    public void simpleTest0() {
        ThetaTree thetaTree = new ThetaTree(4);
        thetaTree.insert(0, 5, 5);
        assertEquals(5, thetaTree.getECT());
        thetaTree.insert(1, 31, 6);
        assertEquals(31, thetaTree.getECT());
        thetaTree.insert(2, 30, 4);
        assertEquals(35, thetaTree.getECT());
        thetaTree.insert(3, 42, 10);
        assertEquals(45, thetaTree.getECT());
        thetaTree.remove(3);
        assertEquals(35, thetaTree.getECT());
        thetaTree.reset();
        assertEquals(Integer.MIN_VALUE, thetaTree.getECT());
    }

}
