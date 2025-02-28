/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.Constants;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.CPIntervalVar;

import static org.maxicp.cp.CPFactory.*;
import static org.junit.jupiter.api.Assertions.*;

class PrecedenceTests extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testStartAtStart(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval1.setStartMax(25);
        interval2.setStartMin(5);

        cp.post(startAtStart(interval1, interval2));

        assertEquals(0, interval1.startMin());
        assertEquals(Constants.HORIZON, interval2.startMax());

        interval1.setPresent();
        cp.fixPoint();

        assertEquals(25, interval2.startMax());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(5, interval1.startMin());

        interval2.setStartMax(15);
        cp.fixPoint();

        assertEquals(15, interval1.startMax());

        interval1.setStartMin(10);
        cp.fixPoint();

        assertEquals(10, interval2.startMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testStartAtStartDelayed(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval1.setStartMax(25);
        interval2.setStartMin(5);

        cp.post(startAtStart(interval1, interval2, 5));

        assertEquals(0, interval1.startMin());
        assertEquals(Constants.HORIZON - 5, interval2.startMax());

        interval1.setPresent();
        cp.fixPoint();

        assertEquals(20, interval2.startMax());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(10, interval1.startMin());

        interval2.setStartMax(15);
        cp.fixPoint();

        assertEquals(20, interval1.startMax());

        interval1.setStartMin(15);
        cp.fixPoint();

        assertEquals(10, interval2.startMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testStartAtEnd(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval1.setStartMax(25);
        interval2.setEndMin(5);

        cp.post(startAtEnd(interval1, interval2));

        assertEquals(0, interval1.startMin());
        assertEquals(Constants.HORIZON, interval2.endMax());

        interval1.setPresent();
        cp.fixPoint();

        assertEquals(25, interval2.endMax());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(5, interval1.startMin());

        interval2.setEndMax(20);
        cp.fixPoint();

        assertEquals(20, interval1.startMax());

        interval1.setStartMin(10);
        cp.fixPoint();

        assertEquals(10, interval2.endMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testStartAtEndDelayed(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval1.setStartMax(25);
        interval2.setEndMin(10);

        cp.post(startAtEnd(interval1, interval2, -5));

        assertEquals(0, interval1.startMin());
        assertEquals(Constants.HORIZON, interval2.endMax());

        interval1.setPresent();
        cp.fixPoint();

        assertEquals(30, interval2.endMax());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(5, interval1.startMin());

        interval2.setEndMax(25);
        cp.fixPoint();

        assertEquals(20, interval1.startMax());

        interval1.setStartMin(10);
        cp.fixPoint();

        assertEquals(15, interval2.endMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEndAtStart(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval1.setEndMax(25);
        interval2.setStartMin(5);

        cp.post(endAtStart(interval1, interval2));

        assertEquals(0, interval1.endMin());
        assertEquals(Constants.HORIZON, interval2.startMax());

        interval1.setPresent();
        cp.fixPoint();

        assertEquals(25, interval2.startMax());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(5, interval1.endMin());

        interval2.setStartMax(20);
        cp.fixPoint();

        assertEquals(20, interval1.endMax());

        interval1.setEndMin(10);
        cp.fixPoint();

        assertEquals(10, interval2.startMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEndAtStartDelayed(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval1.setEndMax(25);
        interval2.setStartMin(5);

        cp.post(endAtStart(interval1, interval2, 5));

        assertEquals(0, interval1.endMin());
        assertEquals(Constants.HORIZON - 5, interval2.startMax());

        interval1.setPresent();
        cp.fixPoint();

        assertEquals(20, interval2.startMax());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(10, interval1.endMin());

        interval2.setStartMax(15);
        cp.fixPoint();

        assertEquals(20, interval1.endMax());

        interval1.setEndMin(15);
        cp.fixPoint();

        assertEquals(10, interval2.startMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEndAtEnd(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval1.setEndMax(25);
        interval2.setEndMin(5);

        cp.post(endAtEnd(interval1, interval2));

        assertEquals(0, interval1.endMin());
        assertEquals(Constants.HORIZON, interval2.endMax());

        interval1.setPresent();
        cp.fixPoint();

        assertEquals(25, interval2.endMax());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(5, interval1.endMin());

        interval2.setEndMax(20);
        cp.fixPoint();

        assertEquals(20, interval1.endMax());

        interval1.setEndMin(10);
        cp.fixPoint();

        assertEquals(10, interval2.endMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEndAtEndDelayed(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval1.setEndMax(25);
        interval2.setEndMin(5);

        cp.post(endAtEnd(interval1, interval2, -5));

        assertEquals(0, interval1.endMin());
        assertEquals(Constants.HORIZON, interval2.endMax());

        interval1.setPresent();
        cp.fixPoint();

        assertEquals(30, interval2.endMax());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(0, interval1.endMin());

        interval2.setEndMax(20);
        cp.fixPoint();

        assertEquals(15, interval1.endMax());

        interval1.setEndMin(10);
        cp.fixPoint();

        assertEquals(15, interval2.endMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testStartBeforeStart(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval1.setStartMin(5);
        interval2.setStartMax(25);

        cp.post(startBeforeStart(interval1, interval2));

        assertEquals(Constants.HORIZON, interval1.startMax());
        assertEquals(0, interval2.startMin());

        interval1.setPresent();
        cp.fixPoint();

        assertEquals(5, interval2.startMin());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(25, interval1.startMax());

        interval1.setStartMin(10);
        cp.fixPoint();

        assertEquals(10, interval2.startMin());

        interval2.setStartMax(20);
        cp.fixPoint();

        assertEquals(20, interval1.startMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testStartBeforeEnd(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval1.setStartMin(5);
        interval2.setEndMax(25);

        cp.post(startBeforeEnd(interval1, interval2));

        assertEquals(Constants.HORIZON, interval1.startMax());
        assertEquals(0, interval2.endMin());

        interval1.setPresent();
        cp.fixPoint();

        assertEquals(5, interval2.endMin());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(25, interval1.startMax());

        interval1.setStartMin(10);
        cp.fixPoint();

        assertEquals(10, interval2.endMin());

        interval2.setEndMax(20);
        cp.fixPoint();

        assertEquals(20, interval1.startMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEndBeforeStart(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval1.setEndMin(5);
        interval2.setStartMax(25);

        cp.post(endBeforeStart(interval1, interval2));

        assertEquals(Constants.HORIZON, interval1.endMax());
        assertEquals(0, interval2.startMin());

        interval1.setPresent();
        cp.fixPoint();

        assertEquals(5, interval2.startMin());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(25, interval1.endMax());

        interval1.setEndMin(10);
        cp.fixPoint();

        assertEquals(10, interval2.startMin());

        interval2.setStartMax(20);
        cp.fixPoint();

        assertEquals(20, interval1.startMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEndBeforeEnd(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval1.setEndMin(5);
        interval2.setEndMax(25);

        cp.post(endBeforeEnd(interval1, interval2));

        assertEquals(Constants.HORIZON, interval1.endMax());
        assertEquals(0, interval2.endMin());

        interval1.setPresent();
        cp.fixPoint();

        assertEquals(5, interval2.endMin());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(25, interval1.endMax());

        interval1.setEndMin(10);
        cp.fixPoint();

        assertEquals(10, interval2.endMin());

        interval2.setEndMax(20);
        cp.fixPoint();

        assertEquals(20, interval1.endMax());
    }
}