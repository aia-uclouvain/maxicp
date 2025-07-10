package org.maxicp.cp.engine.constraints;

import static org.junit.jupiter.api.Assertions.*;

/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

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

import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CostCardinalityMaxDCTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest(CPSolver cp) {
        // TODO
    }

}