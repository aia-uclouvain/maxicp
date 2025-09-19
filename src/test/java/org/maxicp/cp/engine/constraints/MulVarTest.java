/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntVarConstant;
import org.maxicp.cp.engine.core.CPSolver;

import static org.junit.jupiter.api.Assertions.*;

class MulVarTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, -2,2);
        CPIntVar y = CPFactory.makeIntVar(cp, -2,2);
        CPIntVar z = CPFactory.makeIntVar(cp, -100, 100);

        cp.post(new MulVar(x, y, z));

        assertEquals(-4, z.min());
        assertEquals(4, z.max());

        cp.post(CPFactory.ge(x,0));
        cp.post(CPFactory.ge(y,0));
        assertEquals(0, z.min());

        cp.post(CPFactory.neq(z, 0));

        assertEquals(1, x.min());
        assertEquals(1, y.min());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test2(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, 2,4);
        CPIntVar y = CPFactory.makeIntVar(cp, -2,2);
        CPIntVar z = CPFactory.makeIntVar(cp, 4, 100);

        cp.post(new MulVar(x, y, z));

        assertEquals(1, y.min());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test3(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, 2,4);
        CPIntVar y = CPFactory.makeIntVar(cp, -2,2);
        CPIntVar z = CPFactory.makeIntVar(cp, -100, -4);

        cp.post(new MulVar(x, y, z));

        assertEquals(-1, y.max());

    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void test4(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, 1, 6);
        CPIntVar y = CPFactory.makeIntVar(cp, 1, 6);
        CPIntVar z = CPFactory.makeIntVar(cp, 6, 6);

        cp.post(new MulVar(x, y, z));

        cp.post(new Equal(y, new CPIntVarConstant(cp, 1)));

        assertEquals(6, x.min());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test5(CPSolver cp) {

        CPIntVar x = CPFactory.makeIntVar(cp, 1, 6);
        CPIntVar y = CPFactory.makeIntVar(cp, 1, 6);
        CPIntVar z = CPFactory.makeIntVar(cp, 6, 6);

        cp.post(new MulVar(x, y, z));

        cp.post(new Equal(x, new CPIntVarConstant(cp, 1)));

        assertEquals(6, y.min());

    }


}