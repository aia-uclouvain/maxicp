/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.CPFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class EqualTest extends CPSolverTest {

    private static boolean equalDom(CPIntVar x, CPIntVar y) {
        for (int v = x.min(); v < x.max(); v++) {
            if (x.contains(v) && !y.contains(v)) {
                return false;
            }
        }
        for (int v = y.min(); v < y.max(); v++) {
            if (y.contains(v) && !x.contains(v)) {
                return false;
            }
        }
        return true;
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void equal1(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp,0,10);
        CPIntVar y = CPFactory.makeIntVar(cp,0,10);

        cp.post(CPFactory.eq(x,y));

        x.removeAbove(7);
        cp.fixPoint();

        assertTrue(equalDom(x,y));

        y.removeAbove(6);
        cp.fixPoint();

        x.remove(3);
        cp.fixPoint();

        assertTrue(equalDom(x,y));

        x.fix(1);
        cp.fixPoint();

        assertTrue(equalDom(x,y));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void equal2(CPSolver cp) {
        CPIntVar x = CPFactory.makeIntVar(cp,Integer.MAX_VALUE-20,Integer.MAX_VALUE-1);
        CPIntVar y = CPFactory.makeIntVar(cp,Integer.MAX_VALUE-10,Integer.MAX_VALUE-1);

        cp.post(CPFactory.neq(x,Integer.MAX_VALUE-5));

        cp.post(CPFactory.eq(x,y));

        cp.post(CPFactory.eq(x,Integer.MAX_VALUE-1));

        assertEquals(y.min(), Integer.MAX_VALUE-1);

    }


}
