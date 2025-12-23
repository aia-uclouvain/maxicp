/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.StringJoiner;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

class NonOverlapPrecedenceGraphTest extends CPSolverTest {


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBug(CPSolver cp) {
        int n = 5;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp);
            intervals[i].setPresent();
            intervals[i].setLength(1);
            intervals[i].setEndMax(n);
        }

        NoOverlapPrecedenceGraph nonOverlap = new NoOverlapPrecedenceGraph(intervals);
        cp.post(nonOverlap);

        nonOverlap.addPrecedence(0,1);

        assertEquals(4, intervals[0].endMax());
        assertEquals(1, intervals[1].startMin());
        assertEquals(5, intervals[2].endMax());
        assertEquals(0, intervals[2].startMin());

        nonOverlap.addPrecedence(1,2);

        // 0 -> 1 -> 2

        assertEquals(0, intervals[0].startMin());
        assertEquals(1, intervals[1].startMin());
        assertEquals(2, intervals[2].startMin());


        assertEquals(3, intervals[0].endMax());
        assertEquals(4, intervals[1].endMax());
        assertEquals(5, intervals[2].endMax());

        assertEquals(5, intervals[3].endMax());
        assertEquals(0, intervals[3].startMin());

        int preds[] = new int[n];
        int nPreds = nonOverlap.fillPredecessors( 2, preds);
        assertEquals(2, nPreds);



    }

}