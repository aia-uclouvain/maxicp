package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;

import static org.maxicp.cp.CPFactory.makeBoolVar;
import static org.maxicp.cp.CPFactory.makeIntervalVar;
import static org.junit.jupiter.api.Assertions.*;

public class IsEndBeforeStartTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFixBooleanToTrue(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);
        CPBoolVar isBefore = makeBoolVar(cp);

        interval1.setPresent();

        cp.post(new IsEndBeforeStart(interval1, interval2, isBefore));

        assertFalse(isBefore.isFixed());

        interval1.setEndMax(10);
        interval2.setStartMin(10);
        cp.fixPoint();

        assertFalse(isBefore.isFixed());

        interval2.setPresent();
        cp.fixPoint();

        assertTrue(isBefore.isTrue());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFixBooleanToFalse(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);
        CPBoolVar isBefore = makeBoolVar(cp);

        interval1.setPresent();

        cp.post(new IsEndBeforeStart(interval1, interval2, isBefore));

        assertFalse(isBefore.isFixed());

        interval1.setEndMin(11);
        interval2.setStartMax(10);
        // end(A) > start(B)
        cp.fixPoint();

        assertFalse(isBefore.isFixed());

        interval2.setPresent();
        cp.fixPoint();

        assertTrue(isBefore.isFalse());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoChange1(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);
        CPBoolVar isBefore = makeBoolVar(cp);

        interval1.setEndMax(10);
        interval2.setStartMin(11);

        cp.post(new IsEndBeforeStart(interval1, interval2, isBefore));

        assertFalse(isBefore.isFixed());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoChange2(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);
        CPBoolVar isBefore = makeBoolVar(cp);
        isBefore.fix(true);
        interval2.setStartMin(10);
        int endMinBefore = interval1.endMin();
        int endMaxBefore = interval1.endMax();
        int startMinBefore = interval1.startMin();
        int startMaxBefore = interval1.startMax();

        cp.post(new IsEndBeforeStart(interval1, interval2, isBefore));

        assertEquals(endMinBefore, interval1.endMin());
        assertEquals(endMaxBefore, interval1.endMax());
        assertEquals(startMinBefore, interval1.startMin());
        assertEquals(startMaxBefore, interval1.startMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBooleanIsSetToTrue(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);
        CPBoolVar isBefore = makeBoolVar(cp);
        isBefore.fix(true); // end(A) <= start(B) <-> start(B) >= end(A)
        interval1.setEndMin(10);

        cp.post(new IsEndBeforeStart(interval1, interval2, isBefore));

        assertEquals(0, interval2.startMin());

        interval1.setPresent();
        cp.fixPoint();
        // end(A) = [10..horizon]
        // start(B) = [10..horizon]
        assertEquals(10, interval2.startMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBooleanIsSetToFalse(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp);
        CPIntervalVar interval2 = makeIntervalVar(cp);
        CPBoolVar isBefore = makeBoolVar(cp);
        isBefore.fix(false); // end(A) > start(B) <-> start(B) < end(A)
        interval2.setStartMin(10);

        cp.post(new IsEndBeforeStart(interval1, interval2, isBefore));

        // end(A) = [11..horizon]
        // start(B) = [0..10]
        assertEquals(0, interval1.endMin());

        interval2.setPresent();
        cp.fixPoint();

        assertEquals(11, interval1.endMin());
    }


}
