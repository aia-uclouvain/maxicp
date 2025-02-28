/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;

import static org.maxicp.cp.CPFactory.*;

import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.CPIntervalVar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntervalVarEndTest extends CPSolverTest {
    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {

        CPIntervalVar interval = makeIntervalVar(cp);
        interval.setPresent();
        interval.setStartMin(0);
        interval.setLength(2);

        CPIntVar end = CPFactory.makeIntVar(cp, 0, 10);

        cp.post(new IntervalVarEnd(interval, end));

        assertEquals(10, end.max());
        assertEquals(2, end.min());

        interval.setStartMin(5);
        interval.setEndMax(8);

        cp.fixPoint();

        assertEquals(8, end.max());
        assertEquals(7, end.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void test2(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        interval.setPresent();
        interval.setStartMin(0);
        interval.setLength(2);

        CPIntVar end = CPFactory.makeIntVar(cp, 0, 10);

        cp.post(new IntervalVarEnd(interval, end));

        cp.post(CPFactory.equal(end, 5));

        assertEquals(5, interval.endMax());
        assertEquals(3, interval.startMin());
    }
}