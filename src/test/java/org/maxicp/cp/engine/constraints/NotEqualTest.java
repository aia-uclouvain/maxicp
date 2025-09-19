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
import static org.junit.jupiter.api.Assertions.assertFalse;


public class NotEqualTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void notEqualTest(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, 10);
        CPIntVar y = CPFactory.makeIntVar(cp, 10);
        cp.post(CPFactory.neq(x, y));

        cp.post(CPFactory.eq(x, 6));

        assertFalse(y.contains(6));
        assertEquals(9, y.size());
        assertFalse(y.contains(6));
    }

}
