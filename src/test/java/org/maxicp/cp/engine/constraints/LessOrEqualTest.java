/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.CPFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class LessOrEqualTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest0(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -5, 5);
        CPIntVar y = CPFactory.makeIntVar(cp, -10, 10);

        cp.post(new LessOrEqual(x, y));

        assertEquals(-5, y.min());

        y.removeAbove(3);
        cp.fixPoint();

        assertEquals(9, x.size());
        assertEquals(3, x.max());

        x.removeBelow(-4);
        cp.fixPoint();

        assertEquals(-4, y.min());
    }

}
