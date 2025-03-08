/*
 * mini-cp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License  v3
 * as published by the Free Software Foundation.
 *
 * mini-cp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY.
 * See the GNU Lesser General Public License  for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with mini-cp. If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
 *
 * Copyright (c)  2018. by Laurent Michel, Pierre Schaus, Pascal Van Hentenryck
 */

package org.maxicp.cp.engine.constraints;


import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import static org.maxicp.cp.CPFactory.*;


import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.search.Searches.*;

public class AllDifferentFWCTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest1(CPSolver cp) {

        CPIntVar[] x = makeIntVarArray(cp, 5, 5);

        try {
            cp.post(new AllDifferentFWC(x));
            cp.post(eq(x[0], 0));
            for (int i = 1; i < x.length; i++) {
                assertEquals(4, x[i].size());
                assertEquals(1, x[i].min());
            }

        } catch (InconsistencyException e) {
            fail("should not fail");
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest2(CPSolver cp) {

        CPIntVar[] x = makeIntVarArray(cp, 5, 5);

        try {
            cp.post(new AllDifferentFWC(x));
            SearchStatistics stats = makeDfs(cp, firstFail(x)).solve();
            assertEquals(120, stats.numberOfSolutions());
        } catch (InconsistencyException e) {
            fail("should not fail");
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest3(CPSolver cp) {

        CPIntVar[] x = makeIntVarArray(cp, 5, 5);
        cp.post(new AllDifferentFWC(x));

        cp.post(eq(x[2], 3));
        try {
            cp.post(eq(x[1], 3));
            fail();
        } catch (InconsistencyException e) {

        }

    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest4(CPSolver cp) {

        CPIntVar[] x = makeIntVarArray(cp, 8, 8);
        cp.post(new AllDifferentFWC(x));

        try {
            cp.post(eq(x[1], 2));
            cp.post(eq(x[2], 4));

            cp.post(eq(x[6], 1));
            cp.post(eq(x[3], 1));
            fail();
        } catch (InconsistencyException e) {

        }

    }







    @ParameterizedTest
    @MethodSource("getSolver")
    public void allDifferentTest5(CPSolver cp) {

        CPIntVar[] x = makeIntVarArray(cp, 7, 7);

        cp.post(new AllDifferentFWC(x));

        DFSearch search = makeDfs(cp, () -> {
            // selects the unfixed variable with the smallest domain
            CPIntVar xs = selectMin(x, xi -> !xi.isFixed(), xi -> xi.size());
            if (xs == null)
                return EMPTY; // solution found
            // check all diff
            for (int i = 0; i < x.length; i++) {
                if (x[i].isFixed()) {
                    for (int j = 0; j < x.length; j++) {
                        if (i != j) {
                            assertFalse(x[j].contains(x[i].min()),
                                    String.format("x[%d] = {%d} but x[%d] contains the value (its domain is %s)", i, x[i].min(), j, x[j].toString()));
                        }
                    }
                }
            }
            // assign the variable to a value
            int v = xs.min();
            return branch(() -> cp.post(eq(xs, v)),
                    () -> cp.post(neq(xs, v)));
        });

        SearchStatistics stats = search.solve();
        assertEquals(5040, stats.numberOfSolutions());


    }
}
