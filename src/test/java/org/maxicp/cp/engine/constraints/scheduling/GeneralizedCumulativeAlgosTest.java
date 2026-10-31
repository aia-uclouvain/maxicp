/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.Constants;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;

public class GeneralizedCumulativeAlgosTest extends CPSolverTest {

    @Test
    public void testBaseLineConstructorAndEvents() {
        CPSolver cp = makeSolver();
        Activity act = new Activity(makeIntervalVar(cp, false, 4), makeIntVar(cp, 1, 2));
        act.interval().setStartMin(2);
        act.interval().setEndMax(10);

        assertThrows(IllegalArgumentException.class,
                () -> new GeneralizedCumulativeConstraintBaseLine(new Activity[]{act}, 5, 2));

        GeneralizedCumulativeConstraintBaseLine cst = new GeneralizedCumulativeConstraintBaseLine(new Activity[]{act}, 3);
        assertEquals(1, cst.nMax());
        assertEquals(Constants.PIORITY_MEDIUM, cst.priority());

        GeneralizedCumulativeConstraintBaseLine.Event ev = cst.new Event(0, GeneralizedCumulativeConstraintBaseLine.eventType.START_MIN);
        assertEquals(0, ev.getActIdx());
        assertSame(act, ev.getActivity());
        assertEquals(GeneralizedCumulativeConstraintBaseLine.eventType.START_MIN, ev.getType());
        assertEquals(2, ev.getTime());
        assertTrue(ev.toString().contains("START_MIN"));

        ev.reset(0, GeneralizedCumulativeConstraintBaseLine.eventType.START_MAX);
        assertEquals(GeneralizedCumulativeConstraintBaseLine.eventType.START_MAX, ev.getType());
        assertEquals(6, ev.getTime());

        ev.reset(0, GeneralizedCumulativeConstraintBaseLine.eventType.END_MIN);
        assertEquals(6, ev.getTime());

        ev.reset(0, GeneralizedCumulativeConstraintBaseLine.eventType.END_MAX);
        assertEquals(10, ev.getTime());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBaseLinePropagationWithMandatoryAndNegativeHeights(CPSolver cp) {
        Activity act1 = new Activity(makeIntervalVar(cp, false, 4), makeIntVar(cp, 3, 3));
        act1.interval().setStart(0);
        act1.interval().setEnd(4);

        // Optional producer that must become mandatory to keep profile <= 2 on [0, 4)
        Activity act2 = new Activity(makeIntervalVar(cp, true, 1, 6), makeIntVar(cp, -3, 1));
        act2.interval().setStartMin(0);
        act2.interval().setEndMax(6);

        // Absent activity to test removal from activeSet
        Activity act3 = new Activity(makeIntervalVar(cp, true, 2), makeIntVar(cp, 1, 1));
        act3.interval().setAbsent();

        GeneralizedCumulativeConstraintBaseLine cst = new GeneralizedCumulativeConstraintBaseLine(
                new Activity[]{act1, act2, act3}, 0, 2);
        cp.post(cst);

        assertTrue(act2.interval().isPresent());
        assertEquals(0, act2.interval().startMax());
        assertTrue(act2.interval().endMin() >= 4);
        assertTrue(act2.height().max() <= -1);

        // Also test adjustHeightOnFixedPart directly on fixed part
        cst.adjustHeightOnFixedPart(1, 0);
        assertTrue(act2.height().min() >= -3);
        assertTrue(act2.height().max() <= -1);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBaseLineInconsistencyOnOverload(CPSolver cp) {
        Activity act1 = new Activity(makeIntervalVar(cp, false, 4), makeIntVar(cp, 3, 3));
        act1.interval().setStart(0);
        Activity act2 = new Activity(makeIntervalVar(cp, false, 4), makeIntVar(cp, 3, 3));
        act2.interval().setStart(0);

        GeneralizedCumulativeConstraintBaseLine cst = new GeneralizedCumulativeConstraintBaseLine(
                new Activity[]{act1, act2}, 0, 4);
        assertThrows(InconsistencyException.class, () -> cp.post(cst));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBaseLineForwardAndBackwardPushing(CPSolver cp) {
        Activity fixedLeft = new Activity(makeIntervalVar(cp, false, 3), makeIntVar(cp, 2, 2));
        fixedLeft.interval().setStart(0);

        Activity fixedRight = new Activity(makeIntervalVar(cp, false, 3), makeIntVar(cp, 2, 2));
        fixedRight.interval().setStart(7);

        Activity task = new Activity(makeIntervalVar(cp, false, 3), makeIntVar(cp, 1, 2));
        task.interval().setStartMin(0);
        task.interval().setEndMax(10);

        cp.post(new GeneralizedCumulativeConstraintBaseLine(new Activity[]{fixedLeft, fixedRight, task}, 0, 2));
        assertEquals(3, task.interval().startMin());
        assertEquals(7, task.interval().endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBaseLineMinCapacityDeficitMandatory(CPSolver cp) {
        // Need minCapacity = 2 on [0, 4). act1 has fixed part [0, 4) with height 1.
        // act2 is optional on [0, 6) with height [1, 3]; it must become mandatory on [0, 4).
        Activity act1 = new Activity(makeIntervalVar(cp, false, 4), makeIntVar(cp, 1, 1));
        act1.interval().setStart(0);

        Activity act2 = new Activity(makeIntervalVar(cp, true, 2, 6), makeIntVar(cp, 0, 3));
        act2.interval().setStartMin(0);
        act2.interval().setEndMax(6);

        GeneralizedCumulativeConstraintBaseLine cst = new GeneralizedCumulativeConstraintBaseLine(
                new Activity[]{act1, act2}, 2, 5);
        cp.post(cst);

        assertTrue(act2.interval().isPresent());
        assertEquals(1, act2.height().min());
    }

    @Test
    public void testSweepMinAndMaxConstructorsAndExceptions() {
        CPSolver cp = makeSolver();
        Activity act = new Activity(makeIntervalVar(cp, false, 2), makeIntVar(cp, 1, 2));

        assertThrows(IllegalArgumentException.class,
                () -> new GeneralizedCumulativeSweepMin(new Activity[]{act}, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new GeneralizedCumulativeSweepMax(new Activity[]{act}, -1));

        GeneralizedCumulativeSweepMin sweepMin = new GeneralizedCumulativeSweepMin(List.of(act), 1);
        GeneralizedCumulativeSweepMax sweepMax = new GeneralizedCumulativeSweepMax(List.of(act), 2);
        assertEquals(Constants.PIORITY_MEDIUM, sweepMin.priority());
        assertEquals(Constants.PIORITY_MEDIUM, sweepMax.priority());

        GeneralizedCumulativeSweepMin.Event minEv = new GeneralizedCumulativeSweepMin.Event(
                GeneralizedCumulativeSweepMin.EventType.PROFILE, 0, 5, 2);
        assertTrue(minEv.toString().contains("PROFILE"));

        GeneralizedCumulativeSweepMax.Event maxEv = new GeneralizedCumulativeSweepMax.Event(
                GeneralizedCumulativeSweepMax.EventType.PROFILE, 0, 5, 2);
        assertTrue(maxEv.toString().contains("PROFILE"));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSweepMaxPruningAndNegativeGoodProfile(CPSolver cp) {
        // Task 0: compulsory part on [0, 5) with height 3
        Activity act1 = new Activity(makeIntervalVar(cp, false, 5), makeIntVar(cp, 3, 3));
        act1.interval().setStart(0);

        // Task 1: present on [0, 15) with length 4, height 2 -> cannot overlap [0, 5) when maxCapacity = 4
        Activity act2 = new Activity(makeIntervalVar(cp, false, 4), makeIntVar(cp, 2, 2));
        act2.interval().setStartMin(0);
        act2.interval().setEndMax(15);

        // Task 2 & 3: present on [5, 10) with compulsory part and variable height [1, 5], another task on [5, 10) with height 2
        Activity act3 = new Activity(makeIntervalVar(cp, false, 5), makeIntVar(cp, 2, 2));
        act3.interval().setStart(5);

        Activity act4 = new Activity(makeIntervalVar(cp, false, 5), makeIntVar(cp, 1, 5));
        act4.interval().setStart(5);

        cp.post(new GeneralizedCumulativeSweepMax(new Activity[]{act1, act2, act3, act4}, 4));

        assertEquals(10, act2.interval().startMin());
        assertEquals(2, act4.height().max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSweepMaxMandatoryProducerAndEndPruning(CPSolver cp) {
        // Task 0: compulsory part on [4, 8) with height 5, maxCapacity = 3 -> needs negative height producer!
        Activity consumer = new Activity(makeIntervalVar(cp, false, 4), makeIntVar(cp, 5, 5));
        consumer.interval().setStart(4);

        // Optional producer with height -2 and lengthMax 6 on [0, 10) -> must become present and cover [4, 8)
        Activity producer = new Activity(makeIntervalVar(cp, true, 2, 6), makeIntVar(cp, -2, -2));
        producer.interval().setStartMin(0);
        producer.interval().setEndMax(10);

        // Task 2: consumer of height 1, length 3 on [0, 8) -> cannot overlap [4, 8) because 5 - 2 + 1 = 4 > 3
        Activity extra = new Activity(makeIntervalVar(cp, false, 3), makeIntVar(cp, 1, 1));
        extra.interval().setStartMin(0);
        extra.interval().setEndMax(8);

        cp.post(new GeneralizedCumulativeSweepMax(new Activity[]{consumer, producer, extra}, 3));

        assertTrue(producer.interval().isPresent());
        assertTrue(producer.interval().startMax() <= 4);
        assertTrue(producer.interval().endMin() >= 8);
        assertEquals(4, extra.interval().endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSweepMinPruningMandatoryForbiddenAndConsumption(CPSolver cp) {
        // Dummy horizon task on [0, 8) with height 0 so check events are generated
        Activity horizon = new Activity(makeIntervalVar(cp, false, 8), makeIntVar(cp, 0, 0));
        horizon.interval().setStart(0);

        // Producer 1 covers [0, 4) with height 5
        Activity prod1 = new Activity(makeIntervalVar(cp, false, 4), makeIntVar(cp, 5, 5));
        prod1.interval().setStart(0);

        // Producer 2 is optional on [2, 8) with lengthMax 4, height [1, 4]; must cover [4, 8) to reach minCapacity = 2
        Activity prod2 = new Activity(makeIntervalVar(cp, true, 2, 4), makeIntVar(cp, 1, 4));
        prod2.interval().setStartMin(2);
        prod2.interval().setEndMax(8);

        // Negative height activity (consumer) on [0, 8) that is forbidden on [4, 8) since 4 - 3 = 1 < 2
        Activity negAct = new Activity(makeIntervalVar(cp, false, 2), makeIntVar(cp, -3, -3));
        negAct.interval().setStartMin(0);
        negAct.interval().setEndMax(8);

        cp.post(new GeneralizedCumulativeSweepMin(new Activity[]{horizon, prod1, prod2, negAct}, 2));

        assertTrue(prod2.interval().isPresent());
        assertEquals(4, prod2.interval().startMin());
        assertEquals(8, prod2.interval().endMin());
        assertTrue(prod2.height().min() >= 2);
        assertEquals(4, negAct.interval().endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCompareAllThreeCumulativeAlgosWithLeAndAlwaysIn(CPSolver cp) {
        Constants.CumulativeAlgo[] algos = Constants.CumulativeAlgo.values();
        int[] solCountsLe = new int[algos.length];
        int[] solCountsAlwaysIn = new int[algos.length];
        int[] solCountsAlwaysInRange = new int[algos.length];

        for (int a = 0; a < algos.length; a++) {
            Constants.CumulativeAlgo algo = algos[a];

            // 1. Test CPFactory.le(profile, maxCap, algo)
            cp.getStateManager().saveState();
            int n = 3;
            CPIntervalVar[] tasks = new CPIntervalVar[n];
            for (int i = 0; i < n; i++) {
                tasks[i] = makeIntervalVar(cp, false, 2);
                tasks[i].setEndMax(7);
            }
            CPCumulFunction profile = flat();
            for (int i = 0; i < n; i++) {
                profile = CPFactory.plus(profile, pulse(tasks[i], 2));
            }
            cp.post(le(profile, 3, algo));
            CPIntVar[] starts = makeIntVarArray(n, i -> startOr(tasks[i], 0));
            DFSearch dfs = makeDfs(cp, Searches.firstFailBinary(starts));
            solCountsLe[a] = dfs.solve().numberOfSolutions();
            cp.getStateManager().restoreState();

            // 2. Test CPFactory.alwaysIn(profile, minCap, maxCap, algo)
            cp.getStateManager().saveState();
            CPIntervalVar[] tasks2 = new CPIntervalVar[4];
            for (int i = 0; i < 4; i++) {
                tasks2[i] = makeIntervalVar(cp, false, 1);
                tasks2[i].setEndMax(3);
            }
            CPCumulFunction profile2 = flat();
            for (int i = 0; i < 4; i++) {
                profile2 = CPFactory.plus(profile2, pulse(tasks2[i], 1));
            }
            cp.post(alwaysIn(profile2, 1, 2, algo));
            CPIntVar[] starts2 = makeIntVarArray(4, i -> startOr(tasks2[i], 0));
            DFSearch dfs2 = makeDfs(cp, Searches.firstFailBinary(starts2));
            solCountsAlwaysIn[a] = dfs2.solve().numberOfSolutions();
            cp.getStateManager().restoreState();

            // 3. Test CPFactory.alwaysIn(profile, minCap, maxCap, from, to, algo)
            cp.getStateManager().saveState();
            CPIntervalVar[] tasks3 = new CPIntervalVar[3];
            for (int i = 0; i < 3; i++) {
                tasks3[i] = makeIntervalVar(cp, false, 1);
                tasks3[i].setEndMax(3);
            }
            CPCumulFunction profile3 = flat();
            for (int i = 0; i < 3; i++) {
                profile3 = CPFactory.plus(profile3, pulse(tasks3[i], 1));
            }
            cp.post(alwaysIn(profile3, 1, 2, 0, 2, algo));
            CPIntVar[] starts3 = makeIntVarArray(3, i -> startOr(tasks3[i], 0));
            DFSearch dfs3 = makeDfs(cp, Searches.firstFailBinary(starts3));
            solCountsAlwaysInRange[a] = dfs3.solve().numberOfSolutions();
            cp.getStateManager().restoreState();
        }

        assertEquals(solCountsLe[0], solCountsLe[1]);
        assertEquals(solCountsLe[1], solCountsLe[2]);
        assertTrue(solCountsLe[0] > 0);

        assertEquals(solCountsAlwaysIn[0], solCountsAlwaysIn[1]);
        assertEquals(solCountsAlwaysIn[1], solCountsAlwaysIn[2]);
        assertEquals(36, solCountsAlwaysIn[0]);

        assertEquals(solCountsAlwaysInRange[0], solCountsAlwaysInRange[1]);
        assertEquals(solCountsAlwaysInRange[1], solCountsAlwaysInRange[2]);
        assertTrue(solCountsAlwaysInRange[0] > 0);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testCPFactoryCumulAndIntervalHelpers(CPSolver cp) {
        // Empty cumulative function edge cases
        CPCumulFunction empty = flat();
        cp.post(le(empty, 5));
        cp.post(alwaysIn(empty, 0, 5));
        cp.post(alwaysIn(empty, 0, 5, 0, 10));
        assertThrows(InconsistencyException.class, () -> alwaysIn(empty, 1, 5));
        assertThrows(InconsistencyException.class, () -> alwaysIn(empty, 1, 5, 0, 10));

        // step, pulse, stepAtStart, stepAtEnd, sum
        CPIntervalVar iv1 = makeIntervalVar(cp, false, 3);
        iv1.setStart(2);
        CPIntervalVar iv2 = makeIntervalVar(cp, true, 2);
        CPCumulFunction fStep = step(cp, 1, 2);
        CPCumulFunction fPulse = pulse(cp, 0, 5, 1);
        CPCumulFunction fStart = stepAtStart(iv1, 1);
        CPCumulFunction fStartRange = stepAtStart(iv1, 1, 2);
        CPCumulFunction fEnd = stepAtEnd(iv1, 1);
        CPCumulFunction fEndRange = stepAtEnd(iv1, 1, 2);
        CPCumulFunction fVarPulse = pulse(iv1, makeIntVar(cp, 1, 2));
        CPCumulFunction fSum = sum(fStep, fPulse, fStart, fStartRange, fEnd, fEndRange, fVarPulse);
        cp.post(le(fSum, 20));

        // Interval constraint helpers in CPFactory
        cp.post(present(iv2));
        assertTrue(iv2.isPresent());
        CPIntervalVar iv3 = makeIntervalVar(cp, true, 2);
        cp.post(absent(iv3));
        assertTrue(iv3.isAbsent());
        assertEquals(0, status(iv3).max());

        CPIntervalVar iv4 = makeIntervalVar(cp, false, 4);
        cp.post(startAfter(iv4, 2));
        cp.post(startBefore(iv4, 5));
        assertEquals(2, iv4.startMin());
        assertEquals(5, iv4.startMax());
        cp.post(startAt(iv4, 3));
        assertEquals(3, iv4.startMin());
        assertEquals(7, end(iv4).min());
        assertEquals(4, length(iv4).min());
        assertEquals(3, start(iv4).min());

        CPIntervalVar iv5 = makeIntervalVar(cp, false, 2);
        cp.post(endAt(iv5, 6));
        assertEquals(4, iv5.startMin());

        assertSame(iv5, delay(iv5, 0));
        CPIntervalVar delayed = delay(iv5, 2);
        assertEquals(6, delayed.startMin());

        // Precedence & reified interval helpers in CPFactory
        CPIntervalVar a = makeIntervalVar(cp, false, 2);
        CPIntervalVar b = makeIntervalVar(cp, false, 2);
        a.setStart(2);
        cp.post(startAtStart(a, b, 0));
        assertEquals(2, b.startMin());

        CPIntervalVar c = makeIntervalVar(cp, false, 2);
        cp.post(startAtEnd(c, a, 1));
        assertEquals(5, c.startMin());

        CPIntervalVar d = makeIntervalVar(cp, false, 2);
        cp.post(startBeforeStart(a, d, 1));
        cp.post(startBeforeEnd(a, d));
        cp.post(endAtStart(a, c, -1));
        cp.post(endAtEnd(a, b, 0));
        cp.post(endBeforeStart(a, c, 0));
        cp.post(endBeforeEnd(a, c, 0));

        CPBoolVar sbs = isStartBeforeStart(a, c);
        CPBoolVar sbe = isStartBeforeEnd(a, c);
        CPBoolVar ebs = isEndBeforeStart(a, c);
        CPBoolVar ebe = isEndBeforeEnd(a, c);
        assertTrue(sbs.isTrue());
        assertTrue(sbe.isTrue());
        assertTrue(ebs.isTrue());
        assertTrue(ebe.isTrue());

        // Comparison & logical helpers in CPFactory
        CPIntVar x = makeIntVar(cp, 1, 5);
        CPIntVar y = makeIntVar(cp, 1, 5);
        CPBoolVar ord = strictOrder(x, y);
        x.removeBelow(4);
        y.removeAbove(2);
        cp.fixPoint();
        assertTrue(ord.isFalse());

        assertTrue(isGt(x, 3).isTrue());
        assertTrue(isGe(x, 4).isTrue());
        assertTrue(isLt(y, 3).isTrue());
        assertTrue(isGt(x, y).isTrue());
        assertTrue(isGe(x, y).isTrue());
        assertTrue(isLt(y, x).isTrue());
        assertTrue(isNeq(x, y).isTrue());
        assertTrue(isNeq(x, 2).isTrue());

        CPBoolVar b1 = makeBoolVar(cp);
        CPBoolVar b2 = makeBoolVar(cp);
        cp.post(implies(b1, b2));
        b1.fix(true);
        cp.fixPoint();
        assertTrue(b2.isTrue());

        CPIntVar idx = makeIntVar(cp, 0, 2);
        CPIntVar el = elementDC(new int[]{10, 20, 30}, idx);
        idx.fix(1);
        cp.fixPoint();
        assertEquals(20, el.min());
    }
}
