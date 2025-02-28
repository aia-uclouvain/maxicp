/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

class AlternativeTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBasic(CPSolver cp) {
        CPIntervalVar main = makeIntervalVar(cp);
        main.setStartMin(10);
        main.setEndMax(30);
        CPIntervalVar[] alts = {
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp)
        };
        CPIntVar cardinality = makeIntVar(cp, 2, 3);

        cp.post(alternative(main, alts, cardinality));
        cp.fixPoint();

        assertTrue(main.isOptional());
        assertTrue(alts[0].isOptional());
        assertEquals(10, alts[0].startMin());
        assertEquals(30, alts[0].endMax());
        assertTrue(alts[1].isOptional());
        assertEquals(10, alts[1].startMin());
        assertEquals(30, alts[1].endMax());
        assertTrue(alts[2].isOptional());
        assertEquals(10, alts[2].startMin());
        assertEquals(30, alts[2].endMax());
        assertTrue(alts[3].isOptional());
        assertEquals(10, alts[3].startMin());
        assertEquals(30, alts[3].endMax());

        alts[0].setStartMin(15);
        alts[1].setStartMin(16);
        alts[2].setStartMin(17);
        alts[3].setStartMin(18);
        cp.fixPoint();
        assertEquals(15, main.startMin());

        alts[0].setEndMax(22);
        alts[1].setEndMax(23);
        alts[2].setEndMax(24);
        alts[3].setEndMax(25);
        cp.fixPoint();
        assertEquals(25, main.endMax());

        alts[1].setPresent();
        cp.fixPoint();
        assertTrue(main.isPresent());
        assertEquals(16, main.startMin());
        assertEquals(23, main.endMax());

        alts[2].setAbsent();
        alts[3].setAbsent();
        cp.fixPoint();
        assertEquals(2, cardinality.max());
        assertTrue(alts[0].isPresent());
        assertEquals(22, main.endMax());

//        DFSearch dfs = CPFactory.makeDfs(cp, and(branchOnStatus(vars), branchOnPresentStarts(vars)));
//
//        // solutions 7:
//        // -,- both absent
//        // _,0
//        // _,1
//        // 0,_
//        // 1,_
//        // 0,1 both present
//        // 1,0 both present
//
//        SearchStatistics stats = dfs.solve();
//
//        assertEquals(7, stats.numberOfSolutions());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAbsent1(CPSolver cp) {
        CPIntervalVar main = makeIntervalVar(cp);
        main.setStartMin(10);
        main.setEndMax(30);
        CPIntervalVar[] alts = {
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp)
        };
        CPIntVar cardinality = makeIntVar(cp, 2, 3);

        cp.post(alternative(main, alts, cardinality));

        main.setAbsent();
        cp.fixPoint();
        for(CPIntervalVar alt: alts) assertTrue(alt.isAbsent());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAbsent2(CPSolver cp) {
        CPIntervalVar main = makeIntervalVar(cp);
        main.setStartMin(10);
        main.setEndMax(30);
        CPIntervalVar[] alts = {
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp)
        };

        cp.post(alternative(main, alts, 3));

        alts[0].setAbsent();
        alts[1].setAbsent();
        cp.fixPoint();
        assertTrue(main.isAbsent());
        for(CPIntervalVar alt: alts) assertTrue(alt.isAbsent());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCardinality(CPSolver cp) {
        CPIntervalVar main = makeIntervalVar(cp);
        main.setStartMin(10);
        main.setEndMax(30);
        CPIntervalVar[] alts = {
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp),
                makeIntervalVar(cp)
        };
        CPIntVar cardinality = makeIntVar(cp, 0, 4);

        cp.post(alternative(main, alts, cardinality));

        alts[0].setAbsent();
        cp.fixPoint();
        assertEquals(3, cardinality.max());

        alts[1].setPresent();
        cp.fixPoint();
        assertEquals(1, cardinality.min());

        cardinality.removeAbove(1);
        cp.fixPoint();
        assertTrue(alts[2].isAbsent());
        assertTrue(alts[3].isAbsent());
    }
}