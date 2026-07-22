/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BinPackingTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest1(CPSolver cp) {
        int [] itemSizes = new int [] {3, 10, 12, 18, 10};
        CPIntVar[] x = new CPIntVar[itemSizes.length];
        Set<Integer> [] binsForItems = new Set[] {
                Set.of(0, 1),
                Set.of(1, 2),
                Set.of(1, 2),
                Set.of(0, 1),
                Set.of(0, 1)
        };
        for (int i = 0; i < x.length; i++) {
            x[i] = CPFactory.makeIntVar(cp, binsForItems[i]);
        }

        CPIntVar [] loads = new CPIntVar[] {
                CPFactory.makeIntVar(cp, 2, 3),
                CPFactory.makeIntVar(cp, 45, 55),
                CPFactory.makeIntVar(cp, 0, 0)
        };

        cp.post(new BinPacking(x, itemSizes, loads));

        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));

        SearchStatistics stats = dfs.solve();
        assertEquals(1, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest2(CPSolver cp) {
        int [] itemSizes = new int [] {6, 6, 6, 6, 6};
        int nBins = itemSizes.length;
        CPIntVar[] x = CPFactory.makeIntVarArray(cp, nBins, nBins);
        CPIntVar [] loads = CPFactory.makeIntVarArray(cp, nBins, 11);
        cp.post(new BinPacking(x, itemSizes, loads));
        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(x));
        SearchStatistics stats = dfs.solve();
        assertEquals(120, stats.numberOfSolutions());
    }


}