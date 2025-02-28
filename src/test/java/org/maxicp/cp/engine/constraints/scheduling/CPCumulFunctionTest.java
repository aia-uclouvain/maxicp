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

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;

class CPCumulFunctionTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSimple(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp, false, 1);
        CPCumulFunction profile = pulse(interval, 1, 2);
        assertEquals(1, profile.heightAtStart(interval).min());
        assertEquals(2, profile.heightAtStart(interval).max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSimpleOptional(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp, true, 1);
        CPCumulFunction profile = pulse(interval, 1, 2);

        // since the interval is optional the height is 0 since it pulses nothing if absent
        assertEquals(0, profile.heightAtStart(interval).min());
        assertEquals(2, profile.heightAtStart(interval).max());

        assertEquals(0, profile.heightAtEnd(interval).min());
        assertEquals(0, profile.heightAtEnd(interval).max());

        cp.getStateManager().saveState();

        interval.setAbsent();
        cp.fixPoint();

        assertEquals(0, profile.heightAtStart(interval).min());
        assertEquals(0, profile.heightAtStart(interval).max());

        assertEquals(0, profile.heightAtEnd(interval).min());
        assertEquals(0, profile.heightAtEnd(interval).max());

        cp.getStateManager().restoreState();
        cp.getStateManager().saveState();

        interval.setPresent();
        cp.fixPoint();

        assertEquals(1, profile.heightAtStart(interval).min());
        assertEquals(2, profile.heightAtStart(interval).max());

        assertEquals(0, profile.heightAtEnd(interval).min());
        assertEquals(0, profile.heightAtEnd(interval).max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSimpleTwoOptional(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp, true, 1);
        CPCumulFunction profile = pulse(interval, 1, 2);
        profile = new CPPlusCumulFunction(profile, profile);

        // since the interval is optional the height is 0 since it pulses nothing if absent
        assertEquals(0, profile.heightAtStart(interval).min());
        assertEquals(4, profile.heightAtStart(interval).max());

        assertEquals(0, profile.heightAtEnd(interval).min());
        assertEquals(0, profile.heightAtEnd(interval).max());

        cp.getStateManager().saveState();

        interval.setAbsent();
        cp.fixPoint();

        assertEquals(0, profile.heightAtStart(interval).min());
        assertEquals(0, profile.heightAtStart(interval).max());

        assertEquals(0, profile.heightAtEnd(interval).min());
        assertEquals(0, profile.heightAtEnd(interval).max());

        cp.getStateManager().restoreState();
        cp.getStateManager().saveState();

        interval.setPresent();
        cp.fixPoint();

        assertEquals(2, profile.heightAtStart(interval).min());
        assertEquals(4, profile.heightAtStart(interval).max());

        assertEquals(0, profile.heightAtEnd(interval).min());
        assertEquals(0, profile.heightAtEnd(interval).max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCombined(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp, true, 1);
        CPIntervalVar interval2 = makeIntervalVar(cp, true, 1);

        CPCumulFunction pulse1 = pulse(interval1, 1, 2);
        CPCumulFunction pulse2 = pulse(interval2, 1, 2);

        CPCumulFunction profile1 = CPFactory.plus(pulse1, pulse2);
        CPCumulFunction profile2 = CPFactory.plus(pulse1, pulse2);
        CPCumulFunction profile = CPFactory.plus(profile1, profile2);

        CPIntVar h1 = profile.heightAtStart(interval1);
        CPIntVar h2 = profile.heightAtStart(interval2);

        // since the interval is optional the height is 0 since it pulses nothing if absent
        assertEquals(0, h1.min());
        assertEquals(4, h1.max());

        assertEquals(0, h2.min());
        assertEquals(4, h2.max());

        cp.getStateManager().saveState();

        interval1.setPresent();
        interval2.setPresent();
        cp.fixPoint();

        assertEquals(2, h1.min());
        assertEquals(4, h1.max());
        assertEquals(2, h2.min());
        assertEquals(4, h2.max());

        cp.getStateManager().restoreState();
        cp.getStateManager().saveState();

        interval1.setAbsent();
        interval2.setPresent();
        cp.fixPoint();

        assertEquals(0, h1.min());
        assertEquals(0, h1.max());
        assertEquals(2, h2.min());
        assertEquals(4, h2.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testStep(CPSolver cp) {
        CPIntervalVar interval1 = makeIntervalVar(cp, true, 1);
        CPIntervalVar interval2 = makeIntervalVar(cp, true, 1);

        CPCumulFunction p1 = stepAtStart(interval1, 2);
        CPCumulFunction p2 = stepAtStart(interval2, 3);

        CPCumulFunction p = CPFactory.plus(p1, p2);

        CPIntVar h1start = p.heightAtStart(interval1);
        CPIntVar h2start = p.heightAtStart(interval2);

        CPIntVar h1end = p.heightAtEnd(interval1);
        CPIntVar h2end = p.heightAtEnd(interval2);

        // since the interval is optional the height is 0 since no step if absent
        assertEquals(0, h1start.min());
        assertEquals(2, h1start.max());

        assertEquals(0, h2start.min());
        assertEquals(3, h2start.max());

        assertEquals(0, h1end.min());
        assertEquals(2, h1end.max());

        assertEquals(0, h2end.min());
        assertEquals(3, h2end.max());

        cp.getStateManager().saveState();

        interval1.setPresent();
        interval2.setPresent();
        cp.fixPoint();

        assertEquals(2, h1start.min());
        assertEquals(2, h1start.max());
        assertEquals(3, h2start.min());
        assertEquals(3, h2start.max());
        assertEquals(2, h1end.min());
        assertEquals(2, h1end.max());
        assertEquals(3, h2end.min());
        assertEquals(3, h2end.max());

        cp.getStateManager().restoreState();
        cp.getStateManager().saveState();

        interval1.setAbsent();
        interval2.setPresent();
        cp.fixPoint();

        assertEquals(0, h1start.min());
        assertEquals(0, h1start.max());
        assertEquals(3, h2start.min());
        assertEquals(3, h2start.max());
        assertEquals(0, h1end.min());
        assertEquals(0, h1end.max());
        assertEquals(3, h2end.min());
        assertEquals(3, h2end.max());
    }
}