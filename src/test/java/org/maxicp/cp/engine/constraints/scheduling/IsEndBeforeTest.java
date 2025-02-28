package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.cp.CPFactory.*;

public class IsEndBeforeTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFixBooleanToTrue(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        CPIntVar limit = makeIntVar(cp, 0, 20);
        CPBoolVar isBefore = makeBoolVar(cp);

        interval.setPresent();

        cp.post(new IsEndBefore(isBefore, interval, limit));

        assertFalse(isBefore.isFixed());

        interval.setEndMax(10);
        limit.removeBelow(9);
        cp.fixPoint();

        assertFalse(isBefore.isFixed());

        limit.removeBelow(10);
        cp.fixPoint();

        // end = [0..10]
        // limit = [10..20]
        assertTrue(isBefore.isTrue());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFixBooleanToFalse(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        CPIntVar limit = makeIntVar(cp, 0, 20);
        CPBoolVar isBefore = makeBoolVar(cp);

        interval.setPresent();

        cp.post(new IsEndBefore(isBefore, interval, limit));

        assertFalse(isBefore.isFixed());

        interval.setEndMin(11);
        limit.removeAbove(10);
        // end interval: [11..horizon] > [0..10]: limit
        cp.fixPoint();
        assertTrue(isBefore.isFalse());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoChange(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        CPIntVar end = makeIntVar(cp, 0, 20);
        CPBoolVar isBefore = makeBoolVar(cp);

        interval.setEndMax(10);
        end.removeBelow(11);

        cp.post(new IsEndBefore(isBefore, interval, end));

        assertFalse(isBefore.isFixed());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBooleanIsSetToTrue(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        CPIntVar limit = makeIntVar(cp, 0, 20);
        CPBoolVar isBefore = makeBoolVar(cp);
        isBefore.fix(true); // end(interval) <= limit
        interval.setEndMin(10);

        cp.post(new IsEndBefore(isBefore, interval, limit));

        assertEquals(0, limit.min());
        interval.setPresent();

        cp.fixPoint();
        // end(interval) = [10..horizon]
        // limit = [10..horizon]
        assertEquals(10, limit.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBooleanIsSetToFalse(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        CPIntVar limit = makeIntVar(cp, 0, 20);
        CPBoolVar isBefore = makeBoolVar(cp);
        isBefore.fix(false); // end(interval) > limit <-> limit < end(interval)
        interval.setEndMin(10);

        cp.post(new IsEndBefore(isBefore, interval, limit));
        // end of interval must come strictly after limit

        assertEquals(20, limit.max());
        interval.setPresent();
        limit.removeBelow(10);

        cp.fixPoint();
        // limit = [10..horizon]
        // end(interval) = [11..horizon]
        assertEquals(11, interval.endMin());

    }

}
