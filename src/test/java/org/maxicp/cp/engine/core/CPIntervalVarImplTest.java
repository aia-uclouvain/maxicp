/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import static org.maxicp.cp.CPFactory.*;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;

import static org.junit.jupiter.api.Assertions.*;

public class CPIntervalVarImplTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOK(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);

        interval.setStartMin(50);
        interval.setStartMax(100);
        interval.setEndMax(200);
        interval.setEndMin(150);

        // start ∈ [50,100],  length ∈ [50,100], end ∈ [150,200]

        assertEquals(50, interval.startMin());
        assertEquals(100, interval.startMax());
        assertEquals(50, interval.lengthMin());
        assertEquals(150, interval.lengthMax());
        assertEquals(150, interval.endMin());
        assertEquals(200, interval.endMax());
        assertEquals(false, interval.isAbsent());
        assertEquals(false, interval.isPresent());


        // no effect
        interval.setStartMin(5);
        interval.setStartMax(150);
        interval.setLengthMin(20);
        interval.setLengthMax(175);
        interval.setEndMax(300);
        interval.setEndMin(100);

        // start ∈ [50,100],  length ∈ [50,150], end ∈ [150,200]

        interval.setLengthMax(75);
        assertEquals(175,interval.endMax());
        assertEquals(75,interval.startMin());


        // start ∈ [75,100],  length ∈ [50,75], end ∈ [150,175]

        interval.setEndMax(170);

        // start ∈ [75,100],  length ∈ [50,75], end ∈ [150,170]

        assertEquals(170,interval.endMax());
        assertEquals(75,interval.lengthMax());
        assertEquals(50,interval.lengthMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testPresence(CPSolver cp){
        CPIntervalVar interval = makeIntervalVar(cp);

        assertFalse(interval.isPresent());
        assertFalse(interval.isAbsent());

        interval.setPresent();
        assertTrue(interval.isPresent());
        assertFalse(interval.isAbsent());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAbsence(CPSolver cp){
        CPIntervalVar interval = makeIntervalVar(cp);

        assertFalse(interval.isPresent());
        assertFalse(interval.isAbsent());

        interval.setAbsent();
        assertTrue(interval.isAbsent());
        assertFalse(interval.isPresent());

        CPIntervalVar interval2 = makeIntervalVar(cp);

        interval2.setEndMax(20);
        interval2.setStartMin(25);
        assertTrue(interval2.isAbsent());
        assertFalse(interval2.isPresent());

        CPIntervalVar interval3 = makeIntervalVar(cp);

        interval3.setStartMin(20);
        interval3.setEndMax(15);
        assertTrue(interval3.isAbsent());
        assertFalse(interval3.isPresent());
    }


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testStatusVar(CPSolver cp){
        CPIntervalVar interval = makeIntervalVar(cp);
        assertFalse(interval.isPresent());
        CPBoolVar status = interval.status();
        assertFalse(status.isFixed());

        cp.getStateManager().saveState();

        // if the status is true then the interval is present
        cp.post(CPFactory.equal(status,1));
        assertTrue(interval.isPresent());

        cp.getStateManager().restoreState();
        cp.getStateManager().saveState();

        cp.post(CPFactory.equal(status, 0));
        assertTrue(interval.isAbsent());

        cp.getStateManager().restoreState();
        cp.getStateManager().saveState();

        interval.setPresent();
        cp.fixPoint();
        assertTrue(status.isTrue());

        cp.getStateManager().restoreState();
        cp.getStateManager().saveState();

        interval.setAbsent();
        cp.fixPoint();
        assertTrue(status.isFalse());

    }


    @MethodSource("getSolver")
    public void testStatusVarAtConstructor(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp, false, 1);
        assertFalse(interval.isPresent());
        CPBoolVar status = interval.status();
        assertFalse(status.isTrue());
        assertEquals(1, interval.lengthMin());
    }
}