/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.modeling;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.cp.engine.CPSolverTest;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import static org.maxicp.modeling.Factory.*;

public class CumulFunctionTest extends CPSolverTest {


    @Test
    public void simpleCapacity() {
        ModelDispatcher model = makeModelDispatcher();

        IntervalVar interval1 = model.intervalVar(0, 10, 2, true);
        IntervalVar interval2 = model.intervalVar(0, 10, 2, true);

        IntExpression start1 = Factory.start(interval1);
        IntExpression start2 = Factory.start(interval2);

        CumulFunction resource = sum(pulse(interval1,8),pulse(interval2,8));
        model.add(le(resource,8));
        model.add(eq(start1,0));

        ConcreteCPModel cp = model.cpInstantiate();

        Assertions.assertEquals(2, start2.min());
    }


    /*
    // buggy implementation
    @Test
    public void simpleCapacityWithEnergyConstraintOneActivity() {

        ModelDispatcher model = makeModelDispatcher();

        IntervalVar interval1 = model.intervalVar(0, 10, 0,10,1,10, true);

        IntExpression start1 = Factory.start(interval1);
        IntExpression end1 = Factory.end(interval1);
        IntExpression length1 = Factory.length(interval1);

        CumulFunction resource = pulse(interval1,1,10);

        IntExpression height1 = resource.heightAtStart(interval1);

        model.add(eq(mul(height1, length1), 10));

        model.add(eq(start1,0));
        model.add(eq(end1, 1)); // thus height1 = 10, it violates the capacity constraint belose

        model.add(le(resource,8));

        try {
            ConcreteCPModel cp = model.cpInstantiate();
            System.out.println(interval1);
            Assertions.fail();
        } catch (InconsistencyException e) {

        }
    }*/

    /*
    // buggy implementation
    @Test
    public void simpleCapacityWithEnergyConstraintTwoActivities() {

        ModelDispatcher model = makeModelDispatcher();

        IntervalVar interval1 = model.intervalVar(0, 10, 0,10,1,10, true);
        IntervalVar interval2 = model.intervalVar(0, 10, 0,10,1,10, true);

        IntExpression start1 = Factory.start(interval1);
        IntExpression end1 = Factory.end(interval1);
        IntExpression length1 = Factory.length(interval1);

        IntExpression start2 = Factory.start(interval2);
        IntExpression end2 = Factory.end(interval2);
        IntExpression length2 = Factory.length(interval2);

        CumulFunction resource = sum(pulse(interval1,1,10),pulse(interval2,1,10));

        IntExpression height1 = resource.heightAtStart(interval1);
        IntExpression height2 = resource.heightAtStart(interval2);

        model.add(eq(mul(height1, length1), 8));
        model.add(eq(mul(height2, length2), 8));

        model.add(eq(start1,0));
        model.add(eq(end1, 1)); // thus height1 = 8

        model.add(eq(length2,1)); // thus height2 = 8


        model.add(le(resource,8));

        ConcreteCPModel cp = model.cpInstantiate();

        Assertions.assertEquals(8, height1.min());
        Assertions.assertEquals(8, height2.min());

        Assertions.assertEquals(1, start2.min());


    }*/




}
