/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.scheduling;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThetaLambdaTreeTest {

    @Test
    public void simpleTest0() {
        // example from Vilim's thesis p38
        ThetaLambdaTree tree = new ThetaLambdaTree(4);
        tree.insertTheta(0, 0, 5);
        assertEquals(5, tree.getThetaLambdaEct());
        tree.insertTheta(1, 25, 6);
        assertEquals(31, tree.getThetaEct());
        tree.insertTheta(2, 30, 4);
        assertEquals(35, tree.getThetaEct());
        tree.insertTheta(3, 32, 10);
        assertEquals(45, tree.getThetaEct());
        tree.remove(3);
        assertEquals(35, tree.getThetaEct());
        tree.reset();
        assertEquals(Integer.MIN_VALUE, tree.getThetaEct());
    }




    public void simpleTest1() {
        // example from Vilim's thesis p45

        ThetaLambdaTree tree = new ThetaLambdaTree(4);

        tree.insertTheta(0, 5, 5);
        tree.insertTheta(1, 25, 6);
        tree.insertLambda(2, 30, 5);
        tree.insertTheta(3, 32, 10);


        assertEquals(42, tree.getThetaEct());

        assertEquals(46, tree.getThetaLambdaEct());
        assertEquals(2, tree.getResponsibleForThetaLambdaEct());

        tree.remove(2);

        assertEquals(42, tree.getThetaEct());
        assertEquals(42, tree.getThetaLambdaEct());
        assertEquals(ThetaLambdaTree.UNDEF, tree.getResponsibleForThetaLambdaEct());
    }

}
