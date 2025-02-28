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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.cp.CPFactory.makeIntervalVar;

class IntervalVarLengthTest extends CPSolverTest {
    @ParameterizedTest
    @MethodSource("getSolver")
    public void test1(CPSolver cp) {

        CPIntervalVar interval = makeIntervalVar(cp);
        interval.setPresent();
        interval.setLengthMin(2);
        interval.setLengthMax(10);

        CPIntVar length = CPFactory.makeIntVar(cp, 0, 15);

        cp.post(new IntervalVarLength(interval, length));

        assertEquals(10, length.max());
        assertEquals(2, length.min());

        interval.setLengthMin(5);
        interval.setLengthMax(8);

        cp.fixPoint();

        assertEquals(5, length.min());
        assertEquals(8, length.max());

        length.removeAbove(7);
        length.removeBelow(6);

        cp.fixPoint();

        assertEquals(6, interval.lengthMin());
        assertEquals(7, interval.lengthMax());
    }
}