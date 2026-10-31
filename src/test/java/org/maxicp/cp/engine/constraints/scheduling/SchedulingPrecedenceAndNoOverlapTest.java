/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.ArrayList;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;

public class SchedulingPrecedenceAndNoOverlapTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testStartBefore(CPSolver cp) {
        // 1. Present interval filters var and startMax, then deactivates when startMax < var.min()
        CPIntervalVar interval = makeIntervalVar(cp);
        CPIntVar limit = makeIntVar(cp, 2, 10);
        interval.setPresent();
        cp.post(new StartBefore(interval, limit));
        assertEquals(10, interval.startMax());

        interval.setStartMin(5);
        cp.fixPoint();
        assertEquals(5, limit.min());

        interval.setStartMax(6);
        limit.removeBelow(8);
        cp.fixPoint();
        assertEquals(8, limit.min());

        // 2. Optional interval does not filter limit, becomes absent if startMin > limit.max()
        CPIntervalVar opt = makeIntervalVar(cp);
        CPIntVar limit2 = makeIntVar(cp, 2, 10);
        cp.post(new StartBefore(opt, limit2));
        opt.setStartMin(5);
        cp.fixPoint();
        assertEquals(2, limit2.min());

        CPIntervalVar optAbsent = makeIntervalVar(cp);
        optAbsent.setStartMin(15);
        cp.post(new StartBefore(optAbsent, limit2));
        assertTrue(optAbsent.isAbsent());

        // 3. Already absent interval at post()
        CPIntervalVar abs = makeIntervalVar(cp);
        abs.setAbsent();
        CPIntVar limit3 = makeIntVar(cp, 2, 10);
        cp.post(new StartBefore(abs, limit3));
        assertEquals(2, limit3.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEndBeforeDeactivation(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        interval.setPresent();
        CPIntVar limit = makeIntVar(cp, 5, 10);
        cp.post(new EndBefore(interval, limit));
        interval.setEndMax(4);
        limit.removeBelow(6);
        cp.fixPoint();
        assertEquals(4, interval.endMax());
        assertEquals(6, limit.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testEndAfter(CPSolver cp) {
        // 1. Present interval filters var.removeAbove(interval.endMax()) and interval.setEndMin(var.min())
        CPIntervalVar interval = makeIntervalVar(cp, false, 3);
        interval.setEndMax(12);
        CPIntVar limit = makeIntVar(cp, 4, 20);
        cp.post(new EndAfter(interval, limit));
        assertEquals(12, limit.max());
        assertEquals(4, interval.endMin());

        // Deactivate when interval.endMin() > limit.max()
        limit.removeAbove(6);
        interval.setEndMin(8);
        cp.fixPoint();
        assertEquals(8, interval.endMin());
        assertEquals(6, limit.max());

        // 2. Optional interval does not filter limit, becomes absent if endMax < limit.min()
        CPIntervalVar opt = makeIntervalVar(cp);
        opt.setEndMax(10);
        CPIntVar limit2 = makeIntVar(cp, 4, 20);
        cp.post(new EndAfter(opt, limit2));
        assertEquals(20, limit2.max());
        assertEquals(4, opt.endMin());

        CPIntervalVar optAbsent = makeIntervalVar(cp);
        optAbsent.setEndMax(3);
        cp.post(new EndAfter(optAbsent, limit2));
        assertTrue(optAbsent.isAbsent());

        // 3. Already absent interval at post()
        CPIntervalVar abs = makeIntervalVar(cp);
        abs.setAbsent();
        CPIntVar limit3 = makeIntVar(cp, 4, 20);
        cp.post(new EndAfter(abs, limit3));
        assertEquals(4, limit3.min());
        assertEquals(20, limit3.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testIsStartBefore(CPSolver cp) {
        // 1. Fix boolean to true when startMax <= limit.min()
        CPIntervalVar iv1 = makeIntervalVar(cp);
        iv1.setPresent();
        CPIntVar lim1 = makeIntVar(cp, 0, 20);
        CPBoolVar b1 = makeBoolVar(cp);
        cp.post(new IsStartBefore(b1, iv1, lim1));
        assertFalse(b1.isFixed());
        iv1.setStartMax(8);
        lim1.removeBelow(8);
        cp.fixPoint();
        assertTrue(b1.isTrue());

        // 2. Fix boolean to false when startMin > limit.max()
        CPIntervalVar iv2 = makeIntervalVar(cp);
        iv2.setPresent();
        CPIntVar lim2 = makeIntVar(cp, 0, 20);
        CPBoolVar b2 = makeBoolVar(cp);
        cp.post(new IsStartBefore(b2, iv2, lim2));
        iv2.setStartMin(11);
        lim2.removeAbove(10);
        cp.fixPoint();
        assertTrue(b2.isFalse());

        // 3. Boolean preset to true posts StartBefore
        CPIntervalVar iv3 = makeIntervalVar(cp);
        iv3.setPresent();
        iv3.setStartMin(6);
        CPIntVar lim3 = makeIntVar(cp, 0, 12);
        CPBoolVar b3 = makeBoolVar(cp);
        b3.fix(true);
        cp.post(new IsStartBefore(b3, iv3, lim3));
        assertEquals(6, lim3.min());
        assertEquals(12, iv3.startMax());

        // 4. Boolean preset to false posts StartAfter(limit + 1)
        CPIntervalVar iv4 = makeIntervalVar(cp);
        iv4.setPresent();
        CPIntVar lim4 = makeIntVar(cp, 5, 12);
        CPBoolVar b4 = makeBoolVar(cp);
        b4.fix(false);
        cp.post(new IsStartBefore(b4, iv4, lim4));
        assertEquals(6, iv4.startMin());

        // 5. Absent interval deactivates constraint
        CPIntervalVar iv5 = makeIntervalVar(cp);
        CPIntVar lim5 = makeIntVar(cp, 0, 10);
        CPBoolVar b5 = makeBoolVar(cp);
        cp.post(new IsStartBefore(b5, iv5, lim5));
        iv5.setAbsent();
        cp.fixPoint();
        assertFalse(b5.isFixed());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testIsEndBeforeAbsent(CPSolver cp) {
        CPIntervalVar iv = makeIntervalVar(cp);
        CPIntVar lim = makeIntVar(cp, 0, 10);
        CPBoolVar b = makeBoolVar(cp);
        cp.post(new IsEndBefore(b, iv, lim));
        iv.setAbsent();
        cp.fixPoint();
        assertFalse(b.isFixed());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testIsEndAfter(CPSolver cp) {
        // 1. Fix boolean to true when endMin >= end.max()
        CPIntervalVar iv1 = makeIntervalVar(cp);
        iv1.setPresent();
        CPIntVar lim1 = makeIntVar(cp, 0, 10);
        CPBoolVar b1 = makeBoolVar(cp);
        cp.post(new IsEndAfter(b1, iv1, lim1));
        assertFalse(b1.isFixed());
        iv1.setEndMin(10);
        cp.fixPoint();
        assertTrue(b1.isTrue());

        // 2. Fix boolean to false when endMax < end.min()
        CPIntervalVar iv2 = makeIntervalVar(cp);
        iv2.setPresent();
        CPIntVar lim2 = makeIntVar(cp, 8, 20);
        CPBoolVar b2 = makeBoolVar(cp);
        cp.post(new IsEndAfter(b2, iv2, lim2));
        iv2.setEndMax(7);
        cp.fixPoint();
        assertTrue(b2.isFalse());

        // 3. Boolean fixed to true posts EndAfter
        CPIntervalVar iv3 = makeIntervalVar(cp);
        iv3.setPresent();
        iv3.setEndMax(15);
        CPIntVar lim3 = makeIntVar(cp, 5, 20);
        CPBoolVar b3 = makeBoolVar(cp);
        b3.fix(true);
        cp.post(new IsEndAfter(b3, iv3, lim3));
        assertEquals(5, iv3.endMin());
        assertEquals(15, lim3.max());

        // 4. Boolean fixed to false posts EndBefore(end - 1)
        CPIntervalVar iv4 = makeIntervalVar(cp);
        iv4.setPresent();
        CPIntVar lim4 = makeIntVar(cp, 5, 10);
        CPBoolVar b4 = makeBoolVar(cp);
        b4.fix(false);
        cp.post(new IsEndAfter(b4, iv4, lim4));
        assertEquals(9, iv4.endMax());

        // 5. Absent interval deactivates constraint
        CPIntervalVar iv5 = makeIntervalVar(cp);
        CPIntVar lim5 = makeIntVar(cp, 0, 10);
        CPBoolVar b5 = makeBoolVar(cp);
        cp.post(new IsEndAfter(b5, iv5, lim5));
        iv5.setAbsent();
        cp.fixPoint();
        assertFalse(b5.isFixed());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testNoOverlapBinaryAllBranches(CPSolver cp) {
        // 1. B.endMin() > A.startMax() forces A << B (before = true)
        CPIntervalVar a1 = makeIntervalVar(cp, false, 4);
        a1.setStartMax(3);
        CPIntervalVar b1 = makeIntervalVar(cp, false, 4);
        b1.setStartMin(1);
        b1.setEndMax(15);
        NoOverlapBinary nob1 = new NoOverlapBinary(a1, b1);
        cp.post(nob1);
        assertTrue(nob1.before.isTrue());
        assertEquals(4, b1.startMin());

        // 2. A.endMin() > B.startMax() forces B << A (before = false)
        CPIntervalVar a2 = makeIntervalVar(cp, false, 4);
        a2.setStartMin(1);
        a2.setEndMax(15);
        CPIntervalVar b2 = makeIntervalVar(cp, false, 4);
        b2.setStartMax(3);
        NoOverlapBinary nob2 = new NoOverlapBinary(a2, b2);
        cp.post(nob2);
        assertTrue(nob2.before.isFalse());
        assertEquals(4, a2.startMin());

        // 3. Absent interval at post() and during propagate()
        CPIntervalVar a3 = makeIntervalVar(cp, true, 3);
        a3.setAbsent();
        CPIntervalVar b3 = makeIntervalVar(cp, true, 3);
        cp.post(new NoOverlapBinary(a3, b3));

        CPIntervalVar a4 = makeIntervalVar(cp, true, 3);
        CPIntervalVar b4 = makeIntervalVar(cp, true, 3);
        cp.post(new NoOverlapBinary(a4, b4));
        b4.setAbsent();
        cp.fixPoint();
        assertTrue(b4.isAbsent());

        // 4. 3 intervals with makespan and bounded horizon (6 permutations)
        int n = 3;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp, false, (i + 1) * 2);
            intervals[i].setEndMax(20);
        }
        CPIntVar makespan = makespan(intervals);
        ArrayList<CPBoolVar> precedences = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                NoOverlapBinary nonOverlap = new NoOverlapBinary(intervals[i], intervals[j]);
                cp.post(nonOverlap);
                precedences.add(nonOverlap.before);
            }
        }
        Supplier<Runnable[]> fixMakespan = () -> makespan.isFixed() ? EMPTY : new Runnable[]{() -> {
            cp.post(CPFactory.eq(makespan, makespan.min()));
        }};
        DFSearch dfs = CPFactory.makeDfs(cp, and(firstFailBinary(precedences.toArray(new CPBoolVar[0])), fixMakespan));
        SearchStatistics stats = dfs.solve();
        assertEquals(6, stats.numberOfSolutions());
    }

    @Test
    public void testNoOverlapLeftToRightAdditionalBranches() {
        NoOverlapLeftToRight algo = new NoOverlapLeftToRight(4);
        // Trigger notLast where !inserted[acti] and thetaTree.getEct() > startMax[acti]
        int[] startMin = new int[]{0, 0, 2};
        int[] duration = new int[]{3, 3, 2};
        int[] endMax = new int[]{5, 5, 6};
        NoOverlapLeftToRight.Outcome out = algo.filter(startMin, duration, endMax, 3);
        assertNotEquals(NoOverlapLeftToRight.Outcome.NO_CHANGE, out);
    }
}
