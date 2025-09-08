/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class GeneralizedCumulativeConstraintTest extends CPSolverTest {
    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBasicEst(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(2);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act1.interval().setStart(0);
        act1.interval().setLength(5);
        act1.interval().setPresent();
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act2.interval().setLength(5);
        act2.interval().setPresent();
        activities.add(act2);

        GeneralizedCumulativeConstraint cst = new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 1);
        cp.post(cst);

        assertEquals(5, act2.interval().startMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBasicEst1(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(4);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act1.interval().setStart(0);
        act1.interval().setLength(5);
        act1.interval().setPresent();
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act2.interval().setLength(5);
        act2.interval().setPresent();
        activities.add(act2);

        GeneralizedCumulativeConstraint cst = new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 1);

        cp.post(cst);

        assertEquals(5, act2.interval().startMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBasicLct(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(2);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act1.interval().setEndMax(15);
        act1.interval().setLength(5);
        act1.interval().setPresent();
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act2.interval().setEndMax(15);
        act2.interval().setLength(5);
        act2.interval().setPresent();
        activities.add(act2);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 1));
        act2.interval().setStart(10);
        cp.fixPoint();

        assertEquals(10, act1.interval().endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBasicProd(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(2);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 2, 2));
        act1.interval().setLength(10);
        act1.interval().setPresent();
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        act2.interval().setLengthMax(12);
        activities.add(act2);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 1));
        act1.interval().setStartMin(5);
        act1.interval().setEndMax(20);
        cp.fixPoint();

        assertTrue(act2.interval().isPresent());
        assertEquals(3, act2.interval().startMin());
        assertEquals(22, act2.interval().endMax());
        assertTrue(act2.interval().lengthMin() >= 5);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testShrink(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(6);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act1.interval().setPresent();
        act1.interval().setStart(3);
        act1.interval().setLength(1);
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act2.interval().setPresent();
        act2.interval().setStart(5);
        act2.interval().setLength(1);
        activities.add(act2);
        Activity act3 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act3.interval().setPresent();
        act3.interval().setStart(8);
        act3.interval().setLength(1);
        activities.add(act3);
        Activity act4 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act4.interval().setPresent();
        act4.interval().setStart(10);
        act4.interval().setLength(1);
        activities.add(act4);

        Activity act5 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act5.interval().setPresent();
        act5.interval().setLength(2);
        activities.add(act5);
        Activity act6 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act6.interval().setPresent();
        act6.interval().setLength(2);
        activities.add(act6);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 1));
        act5.interval().setStartMin(3);
        act6.interval().setEndMax(11);
        cp.fixPoint();

        assertEquals(6, act5.interval().startMin());
        assertEquals(8, act6.interval().endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMinCapaEst(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(3);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        act1.interval().setLength(5);
        act1.interval().setPresent();
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        act2.interval().setLength(5);
        act2.interval().setPresent();
        activities.add(act2);
        Activity act3 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        activities.add(act3);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 0, 1));
        act1.interval().setStart(0);
        cp.fixPoint();

        assertEquals(5, act2.interval().startMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMinCapaLct(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(3);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        act1.interval().setEndMax(15);
        act1.interval().setLength(5);
        act1.interval().setPresent();
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        act2.interval().setEndMax(15);
        act2.interval().setLength(5);
        act2.interval().setPresent();
        activities.add(act2);
        Activity act3 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        activities.add(act3);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 0, 1));
        act2.interval().setStart(10);
        cp.fixPoint();

        assertEquals(10, act1.interval().endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMinCapaCons(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(2);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        act1.interval().setLength(10);
        act1.interval().setPresent();
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 2, 2));
        //act2.interval().setPresent();
        activities.add(act2);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 0, 1));
        act1.interval().setStartMin(5);
        act1.interval().setEndMax(20);
        cp.fixPoint();

        assertTrue(act2.interval().isPresent());
        assertEquals(5, act2.interval().startMin());
        assertEquals(5, act2.interval().lengthMin());
        assertEquals(20, act2.interval().endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSetProdMult(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(3);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 2, 2));
        act1.interval().setPresent();
        act1.interval().setLength(4);
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        act2.interval().setLength(2);
        act2.interval().setPresent();
        activities.add(act2);
        Activity act3 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        act3.interval().setLength(2);
        activities.add(act3);

        GeneralizedCumulativeConstraint cst = new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 0, 1);
        cp.post(cst);

        act1.interval().setStart(0);
        cp.fixPoint();

        assertEquals(0, act2.interval().startMin());
        assertEquals(4, act2.interval().endMax());
        assertEquals(0, act3.interval().startMin());
        assertEquals(4, act3.interval().endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSetProdMultA(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(3);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 2, 2));
        act1.interval().setPresent();
        act1.interval().setLength(4);
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        act2.interval().setLength(2);
        act2.interval().setPresent();
        activities.add(act2);
        Activity act3 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        act3.interval().setLength(2);
        activities.add(act3);

        GeneralizedCumulativeConstraint cst = new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 0, 1);

        cp.post(cst);
        act1.interval().setStart(1);
        cp.fixPoint();

        assertEquals(1, act2.interval().startMin());
        assertEquals(5, act2.interval().endMax());
        assertEquals(1, act3.interval().startMin());
        assertEquals(5, act3.interval().endMax());
        //assertEquals(CPIntervalVarImpl.HORIZON, act3.interval().endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMinMaxHeightA(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(2);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 2, 2));
        act1.interval().setPresent();
        act1.interval().setLength(4);
        act1.interval().setEnd(4);
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 5));
        act2.interval().setLength(4);
        act2.interval().setStartMin(0);
        act2.interval().setEndMax(8);
        act2.interval().setPresent();
        activities.add(act2);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 1, 2));

        assertEquals(4, act2.interval().startMin());
        assertEquals(4, act2.interval().startMax());
        assertEquals(8, act2.interval().endMin());
        assertEquals(8, act2.interval().endMax());
        assertEquals(1, act2.height().min());
        assertEquals(2, act2.height().max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMinMaxHeightB(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(2);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 2, 2));
        act1.interval().setPresent();
        act1.interval().setLength(4);
        act1.interval().setEnd(4);
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -5, -1));
        act2.interval().setLength(4);
        act2.interval().setStartMin(0);
        act2.interval().setEndMax(8);
        act2.interval().setPresent();
        activities.add(act2);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 1, 2));

        assertEquals(0, act2.interval().startMin());
        assertEquals(0, act2.interval().startMax());

        assertEquals(4, act2.interval().endMin());
        assertEquals(4, act2.interval().endMax());
        assertEquals(-1, act2.height().min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMandatoryMaxHeight(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(2);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 3, 3));
        act1.interval().setPresent();
        act1.interval().setLength(4);
        act1.interval().setEnd(4);
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -5, 5));
        activities.add(act2);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), -5, 1));

        assertEquals(-2, act2.height().max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMandatoryMinHeight(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(2);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -2, -2));
        act1.interval().setPresent();
        act1.interval().setLength(4);
        act1.interval().setEnd(4);
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -5, 5));
        activities.add(act2);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 0, 1));

        assertEquals(2, act2.height().min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMandatoryAct(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(2);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act1.interval().setPresent();
        act1.interval().setLength(4);
        act1.interval().setEnd(4);
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        activities.add(act2);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 2, 3));

        assertTrue(act2.interval().isPresent());
        assertEquals(0, act2.interval().startMax());
        assertEquals(4, act2.interval().endMin());
        assertEquals(4, act2.interval().lengthMin());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testActWithFixedPart(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(2);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 3, 3));
        act1.interval().setPresent();
        act1.interval().setLength(4);
        act1.interval().setEnd(4);
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -2, -1));
        act2.interval().setPresent();
        act2.interval().setLengthMin(3);
        act2.interval().setEndMax(10);
        activities.add(act2);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 2, 2));

        assertTrue(act2.interval().isPresent());
        assertEquals(-1, act2.height().min());
        assertEquals(-1, act2.height().max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void forbiddenFixedPart(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(2);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act1.interval().setStart(3);
        act1.interval().setLength(5);
        act1.interval().setPresent();
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        act2.interval().setStartMin(0);
        act2.interval().setLength(6);
        act2.interval().setEndMax(8);
        activities.add(act2);

        GeneralizedCumulativeConstraint cst = new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 1);

        cp.post(cst);
        assertTrue(act2.interval().isAbsent());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void maxLength1(CPSolver cp) {
        Activity[] activities = new Activity[3];
        activities[0] = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        activities[0].interval().setStart(2);
        activities[0].interval().setLength(1);
        activities[0].interval().setPresent();
        activities[1] = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        activities[1].interval().setStart(7);
        activities[1].interval().setLength(1);
        activities[1].interval().setPresent();
        activities[2] = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        activities[2].interval().setStartMin(0);
        activities[2].interval().setEndMax(11);
        activities[2].interval().setLengthMin(1);
        activities[2].interval().setLengthMax(11);
        activities[2].interval().setPresent();

        GeneralizedCumulativeConstraint cst = new GeneralizedCumulativeConstraint(activities, 1);

        cp.post(cst);
        assertEquals(4, activities[2].getLengthMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void maxLength2(CPSolver cp) {
        Activity[] activities = new Activity[3];
        activities[0] = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        activities[0].interval().setStart(0);
        activities[0].interval().setLength(3);
        activities[0].interval().setPresent();
        activities[1] = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        activities[1].interval().setStart(6);
        activities[1].interval().setLength(4);
        activities[1].interval().setPresent();
        activities[2] = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 2, 2));
        activities[2].interval().setStartMin(0);
        activities[2].interval().setEndMax(10);
        activities[2].interval().setLengthMin(1);
        activities[2].interval().setLengthMax(10);
        activities[2].interval().setPresent();

        GeneralizedCumulativeConstraint cst = new GeneralizedCumulativeConstraint(activities, 1);

        cp.post(cst);
        assertEquals(4, activities[2].getLengthMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void maxLength3(CPSolver cp) {
        Activity[] activities = new Activity[3];
        activities[0] = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        activities[0].interval().setStart(2);
        activities[0].interval().setLength(4);
        activities[0].interval().setPresent();
        activities[1] = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 1));
        activities[1].interval().setStart(9);
        activities[1].interval().setLength(3);
        activities[1].interval().setPresent();
        activities[2] = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        activities[2].interval().setStartMin(0);
        activities[2].interval().setEndMax(12);
        activities[2].interval().setLengthMin(1);
        activities[2].interval().setLengthMax(12);
        activities[2].interval().setPresent();

        GeneralizedCumulativeConstraint cst = new GeneralizedCumulativeConstraint(activities, 0, 10);

        cp.post(cst);
        assertEquals(4, activities[2].getLengthMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testBeldCarlsExample1(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(2);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, 1));
        act1.interval().setPresent();
        act1.interval().setStartMin(1);
        act1.interval().setStartMax(2);
        act1.interval().setLengthMin(2);
        act1.interval().setLengthMax(4);
        act1.interval().setEndMin(3);
        act1.interval().setEndMax(6);
        activities.add(act1);
        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -3, 4));
        act2.interval().setPresent();
        act2.interval().setStartMin(0);
        act2.interval().setStartMax(6);
        act2.interval().setLengthMin(0);
        act2.interval().setLengthMax(2);
        act2.interval().setEndMin(0);
        act2.interval().setEndMax(8);
        activities.add(act2);

        GeneralizedCumulativeConstraint cst = new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 4, 100);

        cp.post(cst);

        assertEquals(1, act2.interval().startMin());
        assertEquals(2, act2.interval().startMax());
        assertEquals(3, act2.interval().endMin());
        assertEquals(4, act2.interval().endMax());
        assertEquals(1, act2.interval().lengthMin());
        assertEquals(3, act2.height().min());
        assertEquals(0, act1.height().min());

        assertEquals(3, act1.interval().lengthMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void ourExample1(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(3);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -5, 1));
        act1.interval().setPresent();
        act1.interval().setStartMin(2);
        act1.interval().setLengthMin(3);
        act1.interval().setEndMax(7);
        activities.add(act1);

        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 3));
        act2.interval().setStartMin(0);
        act2.interval().setLengthMin(2);
        act2.interval().setEndMax(7);
        activities.add(act2);

        Activity act3 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 3, 5));
        act3.interval().setPresent();
        act3.interval().setStartMin(0);
        act3.interval().setLengthMin(2);
        act3.interval().setEndMax(2);
        activities.add(act3);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 2, 3));

        assertEquals(-1, act1.height().min());
        assertEquals(2, act2.interval().startMin());
        assertEquals(5, act2.interval().endMin());
        assertEquals(3, act3.height().max());
        assertTrue(act2.interval().isPresent());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void ourExample2(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(3);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 1, 2));
        act1.interval().setPresent();
        act1.interval().setStartMin(0);
        act1.interval().setLengthMin(3);
        act1.interval().setLengthMax(4);
        act1.interval().setEndMax(4);
        activities.add(act1);

        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 2, 2));
        act2.interval().setPresent();
        act2.interval().setStartMin(2);
        act2.interval().setLengthMin(3);
        act2.interval().setLengthMax(4);
        act2.interval().setEndMax(7);
        activities.add(act2);

        Activity act3 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -2, 1));
        act3.interval().setStartMin(3);
        act3.interval().setLengthMin(1);
        act3.interval().setLengthMax(3);
        act3.interval().setEndMax(9);
        activities.add(act3);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), 0, 1));

        assertTrue(act3.interval().isPresent());
        assertEquals(1, act1.height().min());
        assertEquals(3, act2.interval().startMin());
        assertEquals(6, act2.interval().endMin());
        assertEquals(-1, act3.height().max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void heightAdjust1(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(3);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 3, 6));
        act1.interval().setPresent();
        act1.interval().setStartMin(8);
        act1.interval().setLength(6);
        act1.interval().setEndMax(24);
        activities.add(act1);

        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -4, -4));
        act2.interval().setPresent();
        act2.interval().setStart(0);
        act2.interval().setLength(12);
        activities.add(act2);

        Activity act3 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -4, -4));
        act2.interval().setPresent();
        act3.interval().setStart(20);
        act3.interval().setLength(12);
        activities.add(act3);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), Integer.MIN_VALUE, 4));

        assertEquals(4, act1.height().max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void heightAdjust2(CPSolver cp) {
        List<Activity> activities = new ArrayList<>(4);
        Activity act1 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, 3, 6));
        act1.interval().setPresent();
        act1.interval().setStartMin(8);
        act1.interval().setLength(6);
        act1.interval().setEndMax(24);
        activities.add(act1);

        Activity act2 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -4, -4));
        act2.interval().setPresent();
        act2.interval().setStart(0);
        act2.interval().setLength(12);
        activities.add(act2);

        Activity act3 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -4, -4));
        act2.interval().setPresent();
        act3.interval().setStart(20);
        act3.interval().setLength(12);
        activities.add(act3);

        Activity act4 = new Activity(makeIntervalVar(cp), CPFactory.makeIntVar(cp, -1, -1));
        act2.interval().setPresent();
        act3.interval().setStart(14);
        act3.interval().setLength(4);
        activities.add(act4);

        cp.post(new GeneralizedCumulativeConstraint(activities.toArray(new Activity[0]), Integer.MIN_VALUE, 4));

        assertEquals(5, act1.height().max());
    }

    @Test
    public void bug1() {
        CPSolver cp = makeSolver();

        int n = 3;
        Activity[] activities = new Activity[n];

        CPIntVar[] heights = new CPIntVar[n];
        CPIntVar[] starts = new CPIntVar[n];
        CPIntVar[] ends = new CPIntVar[n];
        CPIntVar[] present = new CPIntVar[n];

        for (int i = 0; i < n; i++) {
            heights[i] = CPFactory.makeIntVar(cp, -3, 3);
            Activity act = new Activity(makeIntervalVar(cp), heights[i]);
            act.interval().setEndMax(4);
            act.interval().setLengthMin(1); // remove
            starts[i] = CPFactory.startOr(act.interval(), 0);
            ends[i] = CPFactory.endOr(act.interval(), 0);
            present[i] = act.interval().status();
            activities[i] = act;
        }

        cp.post(new GeneralizedCumulativeConstraint(activities, 2, 3));

        cp.post(eq(present[0], 1));
        cp.post(eq(present[1], 1));
        cp.post(eq(present[2], 1));

        cp.post(eq(starts[0], 0));
        cp.post(eq(ends[0], 2));
        cp.post(eq(heights[0], -1));

        cp.post(eq(starts[1], 0));
        cp.post(eq(ends[1], 1));
        cp.post(eq(heights[1], 0));

        cp.post(eq(starts[2], 0));
        cp.post(eq(ends[2], 3));
        cp.post(eq(heights[2], 3));
        // should not fail
    }


    @Test
    public void bug2() {
        CPSolver cp = makeSolver();

        int n = 3;
        Activity[] activities = new Activity[n];

        CPIntVar[] heights = new CPIntVar[n];
        CPIntVar[] starts = new CPIntVar[n];
        CPIntVar[] ends = new CPIntVar[n];
        CPIntVar[] present = new CPIntVar[n];

        for (int i = 0; i < n; i++) {
            heights[i] = CPFactory.makeIntVar(cp, -3, 3);
            Activity act = new Activity(makeIntervalVar(cp), heights[i]);
            act.interval().setEndMax(4);
            act.interval().setLengthMin(1); // remove
            starts[i] = CPFactory.startOr(act.interval(), 0);
            ends[i] = CPFactory.endOr(act.interval(), 0);
            present[i] = act.interval().status();
            activities[i] = act;
        }

        cp.post(new GeneralizedCumulativeConstraint(activities, 2, 3));

        cp.post(eq(present[0], 0));
        cp.post(eq(present[1], 1));
        cp.post(eq(present[2], 1));

        cp.post(eq(starts[1], 0));
        cp.post(eq(ends[1], 1));
        cp.post(eq(heights[1], 1));

        cp.post(eq(starts[2], 0));
        cp.post(eq(ends[2], 4));
        cp.post(eq(heights[2], 2));
        // should not fail
    }

    @Test
    public void bug3() {
        CPSolver cp = makeSolver();

        int n = 3;
        Activity[] activities = new Activity[n];

        CPIntVar[] heights = new CPIntVar[n];
        CPIntVar[] starts = new CPIntVar[n];
        CPIntVar[] ends = new CPIntVar[n];
        CPIntVar[] present = new CPIntVar[n];

        for (int i = 0; i < n; i++) {
            heights[i] = CPFactory.makeIntVar(cp, -3, 3);
            Activity act = new Activity(makeIntervalVar(cp), heights[i]);
            act.interval().setEndMax(4);
            act.interval().setLengthMin(1); // remove
            starts[i] = CPFactory.startOr(act.interval(), 0);
            ends[i] = CPFactory.endOr(act.interval(), 0);
            present[i] = act.interval().status();
            activities[i] = act;
        }

        cp.post(new GeneralizedCumulativeConstraint(activities, 2, 3));

        cp.post(eq(present[0], 1));
        cp.post(eq(present[1], 0));
        cp.post(eq(present[2], 1));

        cp.post(eq(starts[0], 0));
        cp.post(eq(ends[0], 4));
        cp.post(eq(heights[0], 3));

        cp.post(eq(starts[2], 2));
        cp.post(eq(ends[2], 4));
        cp.post(eq(heights[2], -1));
        // should not fail
    }

    @Test
    public void bug4() {
        CPSolver cp = makeSolver();

        int n = 3;
        Activity[] activities = new Activity[n];

        CPIntVar[] heights = new CPIntVar[n];
        CPIntVar[] starts = new CPIntVar[n];
        CPIntVar[] ends = new CPIntVar[n];
        CPIntVar[] present = new CPIntVar[n];

        for (int i = 0; i < n; i++) {
            heights[i] = CPFactory.makeIntVar(cp, -3, 3);
            Activity act = new Activity(makeIntervalVar(cp), heights[i]);
            act.interval().setEndMax(4);
            act.interval().setLengthMin(1); // remove
            starts[i] = CPFactory.startOr(act.interval(), 0);
            ends[i] = CPFactory.endOr(act.interval(), 0);
            present[i] = act.interval().status();
            activities[i] = act;
        }

        cp.post(new GeneralizedCumulativeConstraint(activities, 2, 3));

        cp.post(eq(present[0], 1));
        cp.post(eq(present[1], 1));
        cp.post(eq(present[2], 1));

        cp.post(eq(starts[0], 1));
        cp.post(eq(ends[0], 4));
        cp.post(eq(heights[0], 1));

        cp.post(eq(starts[1], 3));
        cp.post(eq(ends[1], 4));
        cp.post(eq(heights[1], 1));

        cp.post(eq(starts[2], 1));
        cp.post(eq(ends[2], 3));
        cp.post(eq(heights[2], 1));
        // should not fail
    }

    @Test
    public void bug5() {
        CPSolver cp = makeSolver();

        int n = 3;
        Activity[] activities = new Activity[n];

        CPIntVar[] heights = new CPIntVar[n];
        CPIntVar[] starts = new CPIntVar[n];
        CPIntVar[] ends = new CPIntVar[n];
        CPIntVar[] present = new CPIntVar[n];

        for (int i = 0; i < n; i++) {
            heights[i] = CPFactory.makeIntVar(cp, -3, 3);
            Activity act = new Activity(makeIntervalVar(cp), heights[i]);
            act.interval().setEndMax(4);
            act.interval().setLengthMin(1); // remove
            starts[i] = CPFactory.startOr(act.interval(), 0);
            ends[i] = CPFactory.endOr(act.interval(), 0);
            present[i] = act.interval().status();
            activities[i] = act;
        }

        cp.post(new GeneralizedCumulativeConstraint(activities, 2, 3));

        cp.post(eq(present[0], 1));
        cp.post(eq(present[1], 1));
        cp.post(eq(present[2], 1));

        cp.post(eq(starts[0], 1));
        cp.post(eq(ends[0], 4));
        cp.post(eq(heights[0], 1));

        cp.post(eq(starts[1], 1));
        cp.post(eq(ends[1], 3));
        cp.post(eq(heights[1], 1));

        assertThrowsExactly(InconsistencyException.class, () -> cp.post(eq(starts[2], 0)));
        // should fail
    }

    @Test
    public void bug6() {
        CPSolver cp = makeSolver();

        int n = 3;
        Activity[] activities = new Activity[n];

        CPIntVar[] heights = new CPIntVar[n];
        CPIntVar[] starts = new CPIntVar[n];
        CPIntVar[] ends = new CPIntVar[n];
        CPIntVar[] present = new CPIntVar[n];

        for (int i = 0; i < n; i++) {
            heights[i] = CPFactory.makeIntVar(cp, -3, 3);
            Activity act = new Activity(makeIntervalVar(cp), heights[i]);
            act.interval().setEndMax(4);
            act.interval().setLengthMin(1); // remove
            starts[i] = CPFactory.startOr(act.interval(), 0);
            ends[i] = CPFactory.endOr(act.interval(), 0);
            present[i] = act.interval().status();
            activities[i] = act;
        }

        cp.post(new GeneralizedCumulativeConstraint(activities, 2, 3));

        cp.post(eq(present[0], 1));
        cp.post(eq(present[1], 1));
        cp.post(eq(present[2], 1));

        cp.post(eq(starts[0], 2));
        cp.post(eq(ends[0], 4));
        cp.post(eq(heights[0], 3));

        cp.post(eq(starts[1], 3));
        cp.post(eq(ends[1], 4));
        cp.post(eq(heights[1], -2));

        cp.post(eq(starts[2], 3));
        cp.post(eq(ends[2], 4));
        cp.post(eq(heights[2], 1));
        // should not fail
    }

    @Test
    public void bug7() {
        CPSolver cp = makeSolver();

        int n = 3;
        Activity[] activities = new Activity[n];

        CPIntVar[] heights = new CPIntVar[n];
        CPIntVar[] starts = new CPIntVar[n];
        CPIntVar[] ends = new CPIntVar[n];
        CPIntVar[] present = new CPIntVar[n];

        for (int i = 0; i < n; i++) {
            heights[i] = CPFactory.makeIntVar(cp, -3, 3);
            Activity act = new Activity(makeIntervalVar(cp), heights[i]);
            act.interval().setEndMax(4);
            act.interval().setLengthMin(1); // remove
            starts[i] = CPFactory.startOr(act.interval(), 0);
            ends[i] = CPFactory.endOr(act.interval(), 0);
            present[i] = act.interval().status();
            activities[i] = act;
        }

        cp.post(new GeneralizedCumulativeConstraint(activities, 2, 3));

        cp.post(eq(present[0], 1));
        cp.post(eq(present[1], 1));
        cp.post(eq(present[2], 1));

        cp.post(eq(starts[0], 0));
        cp.post(eq(ends[0], 2));
        cp.post(eq(heights[0], 2));

        cp.post(eq(starts[1], 3));
        cp.post(eq(ends[1], 4));
        cp.post(eq(heights[1], 1));

        cp.post(eq(starts[2], 3));
        cp.post(eq(ends[2], 4));
        cp.post(eq(heights[2], 1));
        // should not fail
    }

    @Test
    public void bug8() {
        CPSolver cp = makeSolver();

        int n = 6;
        int[] l = new int[]{2, 3, 1, 4, 2, 5};
        int[] h = new int[]{4, 1, 3, -2, 5, -1};

        Activity[] activities = new Activity[n];
        CPIntVar[] heights = new CPIntVar[n];
        CPIntVar[] starts = new CPIntVar[n];

        for (int i = 0; i < n; i++) {
            heights[i] = CPFactory.makeIntVar(cp, h[i], h[i]);
            Activity act = new Activity(makeIntervalVar(cp), heights[i]);
            act.interval().setEndMax(8);
            act.interval().setLength(l[i]);
            act.interval().setPresent();
            starts[i] = CPFactory.startOr(act.interval(), 0);
            activities[i] = act;
        }

        cp.post(new GeneralizedCumulativeConstraint(activities, 2));
        cp.post(eq(starts[0], 0));

        assertTrue(activities[1].isFixed());
        assertEquals(5, starts[1].min());

        assertTrue(activities[2].isFixed());
        assertEquals(4, starts[2].min());

        assertTrue(activities[3].isFixed());
        assertEquals(0, starts[3].min());

        assertTrue(activities[4].isFixed());
        assertEquals(2, starts[4].min());

        assertFalse(activities[5].isFixed());
        assertEquals(0, starts[5].min());
        assertEquals(2, starts[5].max());
        assertEquals(5, activities[5].interval().endMin());
        assertEquals(7, activities[5].interval().endMax());
    }

    record sol(int start0, int end0, int height0, boolean present0, int start1, int end1, int height1, boolean present1,
               int start2, int end2, int height2, boolean present2) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            sol sol = (sol) o;
            return start0 == sol.start0 && end0 == sol.end0 && height0 == sol.height0 && present0 == sol.present0 && start1 == sol.start1 && end1 == sol.end1 && height1 == sol.height1 && present1 == sol.present1 && start2 == sol.start2 && end2 == sol.end2 && height2 == sol.height2 && present2 == sol.present2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(start0, end0, height0, present0, start1, end1, height1, present1, start2, end2, height2, present2);
        }
    }

    // /!\ Slow
    @Disabled
    @Test
    public void hybridExample() {
        CPSolver cp = CPFactory.makeSolver();

        int n = 3;
        Activity[] activities = new Activity[n];

        CPIntVar[] heights = new CPIntVar[n];
        CPIntVar[] starts = new CPIntVar[n];
        CPIntVar[] ends = new CPIntVar[n];
        CPIntVar[] present = new CPIntVar[n];

        for (int i = 0; i < n; i++) {
            heights[i] = CPFactory.makeIntVar(cp, -3, 3);
            Activity act = new Activity(makeIntervalVar(cp), heights[i]);
            act.interval().setEndMax(4);
            act.interval().setLengthMin(1); // remove
            starts[i] = CPFactory.startOr(act.interval(), 0);
            ends[i] = CPFactory.endOr(act.interval(), 0);
            present[i] = act.interval().status();
            activities[i] = act;
        }
        HashSet<sol> sols1 = new HashSet<>();

        Supplier<Runnable[]> search = new Supplier<Runnable[]>() {
            @Override
            public Runnable[] get() {
                for (int i = 0; i < n; i++) {
                    final int idx = i;
                    if (!present[i].isFixed()) {
                        return branch(() -> cp.post(eq(present[idx], 0)), () -> cp.post(eq(present[idx], 1)));
                    }
                }
                for (int i = 0; i < n; i++) {
                    if (present[i].isFixed() && present[i].min() == 1) {
                        if (!starts[i].isFixed()) {
                            return binaryBranchMin(starts[i]);
                        } else if (!ends[i].isFixed()) {
                            return binaryBranchMin(ends[i]);
                        } else if (!heights[i].isFixed()) {
                            return binaryBranchMin(heights[i]);
                        }
                    }
                }
                return EMPTY;
            }
        };

        DFSearch dfs1 = new DFSearch(cp.getStateManager(), search);

        dfs1.onSolution(() -> {
            sols1.add(new sol(
                    present[0].max() == 0 ? -1 : starts[0].min(), present[0].max() == 0 ? -1 : ends[0].min(), present[0].max() == 0 ? -1 : heights[0].min(), present[0].min() == 1,
                    present[1].max() == 0 ? -1 : starts[1].min(), present[1].max() == 0 ? -1 : ends[1].min(), present[1].max() == 0 ? -1 : heights[1].min(), present[1].min() == 1,
                    present[2].max() == 0 ? -1 : starts[2].min(), present[2].max() == 0 ? -1 : ends[2].min(), present[2].max() == 0 ? -1 : heights[2].min(), present[2].min() == 1));
        });

        SearchStatistics stats1 = dfs1.solveSubjectTo(limit -> false, () -> {
            cp.post(new GeneralizedCumulativeChecker(activities, 2, 3));
        });

        HashSet<sol> sols2 = new HashSet<>();

        DFSearch dfs2 = new DFSearch(cp.getStateManager(), search);

        dfs2.onSolution(() -> {
            sols2.add(new sol(
                    present[0].max() == 0 ? -1 : starts[0].min(), present[0].max() == 0 ? -1 : ends[0].min(), present[0].max() == 0 ? -1 : heights[0].min(), present[0].min() == 1,
                    present[1].max() == 0 ? -1 : starts[1].min(), present[1].max() == 0 ? -1 : ends[1].min(), present[1].max() == 0 ? -1 : heights[1].min(), present[1].min() == 1,
                    present[2].max() == 0 ? -1 : starts[2].min(), present[2].max() == 0 ? -1 : ends[2].min(), present[2].max() == 0 ? -1 : heights[2].min(), present[2].min() == 1));
        });

        SearchStatistics stats2 = dfs2.solveSubjectTo(limit -> false, () -> {
            cp.post(new GeneralizedCumulativeConstraint(activities, 2, 3));
        });

        // debugging purpose: print solutions that were discarded wrongly by the constraint
        HashSet<sol> toStudy1 = new HashSet<>();
        for (sol s : sols1) {
            if (!sols2.contains(s)) {
                toStudy1.add(s);
                System.out.println(s);
            }
        }

        // debugging purpose: print solutions that were wrongly not discarded by the constraint
        HashSet<sol> toStudy2 = new HashSet<>();
        for (sol s : sols2) {
            if (!sols1.contains(s)) {
                toStudy2.add(s);
                System.out.println(s);
            }
        }

        assertEquals(stats1.numberOfSolutions(), stats2.numberOfSolutions());
    }

    /**
     * Binary branching on the minimum value of a variable.
     * @param var the variable to branch on
     * @return the two branches, the left branch fixing the variable to its minimum value,
     * the right branch removing this value from the domain.
     */
    public static Runnable[] binaryBranchMin(CPIntVar var) {
        int min = var.min();
        if (var.isFixed()) return EMPTY;
        Runnable left = () -> {
            var.getSolver().post(eq(var, min));
        };
        Runnable right = () -> {
            var.getSolver().post(neq(var, min));
        };
        return new Runnable[]{left,right};
    }
}