package org.maxicp.cp.engine.constraints;

import static org.junit.jupiter.api.Assertions.*;

/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

import org.junit.Assert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CostCardinalityMaxDCTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 5, 3);
        int[] upper= {3, 2, 2};
        int[][] costs = new int[5][3];

        for (int i = 0; i < 5; i++) {
            costs[i][0] = 1;
            costs[i][1] = 2;
            costs[i][2] = 3;
        }
        CostCardinalityMaxDC constraint = new CostCardinalityMaxDC(x, upper, costs, 7);
        cp.post(constraint);

        assertEquals(7, constraint.getMinCostAssignment());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTestInconsistency(CPSolver cp) {
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, 5, 3);
        int[] upper= {3, 2, 2};
        int[][] costs = new int[5][3];

        for (int i = 0; i < 5; i++) {
            costs[i][0] = 1;
            costs[i][1] = 2;
            costs[i][2] = 3;
        }
        CostCardinalityMaxDC constraint = new CostCardinalityMaxDC(x, upper, costs, 6);


        assertThrowsExactly(InconsistencyException.class, ()->cp.post(constraint));
    }

}