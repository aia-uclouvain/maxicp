/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.rules.ExpectedException;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.search.Searches.firstFail;

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

}
