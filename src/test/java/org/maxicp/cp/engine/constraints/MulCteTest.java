/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.util.exception.InconsistencyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.search.Searches.firstFailBinary;

public class MulCteTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, 2, 5);
        CPIntVar y = CPFactory.makeIntVar(cp, 10, 10);
        try {
            cp.post(new MulCte(x, 3, y));
            fail();
        } catch (InconsistencyException e) {
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test2(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, 2, 5);
        CPIntVar z = CPFactory.makeIntVar(cp, 10, 12);
        cp.post(new MulCte(x, 3, z));
        assertTrue(x.isFixed());
        assertEquals(4, x.min());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void test3(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, 2, 5);
        CPIntVar z = CPFactory.makeIntVar(cp, 9, 12);
        cp.post(new MulCte(x, 3, z));
        cp.post(CPFactory.le(z,11));
        assertTrue(x.isFixed());
        assertEquals(3, x.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test4(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp, -5, 5);
        CPIntVar z = CPFactory.makeIntVar(cp, -9, 12);
        cp.post(new MulCte(x, 0, z));
        assertFalse(x.isFixed());
        assertEquals(-5, x.min());
        assertEquals(5, x.max());
        assertTrue(z.isFixed());
        assertEquals(0, z.min());
    }

}
