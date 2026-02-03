package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.maxicp.cp.CPFactory.makeIntVar;
import static org.maxicp.cp.CPFactory.makeIntervalVar;

public class EndBeforeTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFilterPresent(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        CPIntVar limit = makeIntVar(cp, 2, 10);
        interval.setPresent();
        cp.post(new EndBefore(interval, limit));
        // limit = [2..10]
        assertEquals(10, interval.endMax());

        interval.setEndMin(5);
        cp.fixPoint();
        // end = [5..10] and present
        assertEquals(5, limit.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFilterOptional(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        CPIntVar limit = makeIntVar(cp, 2, 10);
        cp.post(new EndBefore(interval, limit));
        // limit = [2..10]
        assertEquals(10, interval.endMax());

        interval.setEndMin(5);
        cp.fixPoint();
        // end = [5..10] but optional
        assertEquals(2, limit.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testFilterAbsent(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        interval.setEndMin(5);
        interval.setAbsent();
        CPIntVar limit = makeIntVar(cp, 2, 10);
        cp.post(new EndBefore(interval, limit));
        assertEquals(2, limit.min());
        assertEquals(10, limit.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testForceAbsent(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        CPIntVar limit = makeIntVar(cp, 2, 10);
        interval.setEndMin(20);
        cp.post(new EndBefore(interval, limit));
        // limit = [2..10]
        assertTrue(interval.isAbsent());
    }

}
