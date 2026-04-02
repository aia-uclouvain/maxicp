/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.modeling;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import static org.maxicp.modeling.Factory.*;

public class IntervalVarTest extends CPSolverTest {

    @Test
    public void testStartEndEqual() {
        ModelDispatcher model = makeModelDispatcher();

        IntervalVar interval = model.intervalVar(0, 10, 1, true);

        IntExpression start = Factory.start(interval);
        IntExpression end = Factory.end(interval);
        IntExpression length = Factory.length(interval);

        // model.add(eq(1,length));
        model.add(eq(2,start));

        ConcreteCPModel cp = model.cpInstantiate();

        Assertions.assertEquals(1,length.min());
        Assertions.assertEquals(1,length.max());
        Assertions.assertEquals(2,start.min());
        Assertions.assertEquals(2,start.max());
        Assertions.assertEquals(3,end.min());
        Assertions.assertEquals(3,end.max());
    }

    @Test
    public void testStartEndLengthInterval() {
        ModelDispatcher model = makeModelDispatcher();

        IntervalVar interval = model.intervalVar(false);

        model.add(present(interval));
        model.add(eq(length(interval),2));
        model.add(eq(start(interval),0));

        ConcreteCPModel cp = model.cpInstantiate();


        Assertions.assertTrue(interval.isPresent());
        Assertions.assertEquals(2,interval.lengthMin());
        Assertions.assertEquals(2,interval.lengthMax());
        Assertions.assertEquals(0,interval.startMin());
        Assertions.assertEquals(0,interval.startMax());
        Assertions.assertEquals(2,interval.endMin());
        Assertions.assertEquals(2,interval.endMax());


    }


    @Test
    public void testLengthEqual() {
        ModelDispatcher model = makeModelDispatcher();

        IntervalVar interval = model.intervalVar(0, 10, 0, 10, 0, 10, true);

        IntExpression start = Factory.start(interval);
        IntExpression end = Factory.end(interval);
        IntExpression length = Factory.length(interval);

        model.add(eq(3,end));
        model.add(eq(1,length));

        ConcreteCPModel cp = model.cpInstantiate();

        Assertions.assertEquals(1,length.min());
        Assertions.assertEquals(1,length.max());
        Assertions.assertEquals(2,start.min());
        Assertions.assertEquals(2,start.max());
        Assertions.assertEquals(3,end.min());
        Assertions.assertEquals(3,end.max());
    }


    @Test
    public void testEnergyConstraintWithHeightAndVariableLength() {
        ModelDispatcher model = makeModelDispatcher();

        IntervalVar interval = model.intervalVar(0, 10, 0, 10, 0, 10, true);

        CumulFunction resource = flat();

        resource = Factory.pulse(interval,1,10);

        IntExpression height = resource.heightAtStart(interval);
        IntExpression length = Factory.length(interval);
        IntExpression start = Factory.start(interval);
        IntExpression end = Factory.end(interval);

        model.add(eq(mul(length,height),5));

        ConcreteCPModel cp = model.cpInstantiate();

        DFSearch dfs = cp.dfSearch(Searches.firstFailBinary(start,end));
        dfs.onSolution(() -> {
            int s = start.min();
            int e = end.min();
            int l = length.min();
            int h = height.min();
            Assertions.assertEquals(5,l*h);
            Assertions.assertTrue(height.isFixed());
        });
        SearchStatistics stats = dfs.solve();
        Assertions.assertEquals(16,stats.numberOfSolutions());
    }

    @Test
    public void testBranchingConstraints() {
        ModelDispatcher model = makeModelDispatcher();
        IntervalVar var = model.intervalVar(0, 10, 5, false);
        int splitValue = 5;

        // Test present(var)
        model.add(present(var));
        ConcreteCPModel cp1 = model.cpInstantiate();
        Assertions.assertFalse(var.isOptional());
        Assertions.assertTrue(var.isPresent());

        // Test startBefore(var, splitValue)
        // start(var) < split
        model.add(startBefore(var, splitValue));
        ConcreteCPModel cp2 = model.cpInstantiate();
        Assertions.assertTrue(var.startMax() <= splitValue);

        // Test le(length(var), splitValue)
        // len(var) <= split
        model.add(le(length(var), splitValue));
        ConcreteCPModel cp3 = model.cpInstantiate();
        Assertions.assertTrue(var.lengthMax() <= splitValue);


        // Test startAfter(var, splitValue)
        // start(var) > split
        model.add(startAfter(var, splitValue));
        ConcreteCPModel cp5 = model.cpInstantiate();
        Assertions.assertTrue(var.startMin() >= splitValue);

    }

    @Test
    public void testIsAbsent() {
        ModelDispatcher model = makeModelDispatcher();
        IntervalVar var = model.intervalVar(0, 10, 5, false);
        Assertions.assertTrue(!var.isPresent());
        Assertions.assertTrue(var.isOptional());
        // Test present(var)
        model.add(not(present(var)));
        ConcreteCPModel cp1 = model.cpInstantiate();
        Assertions.assertFalse(var.isPresent());
        Assertions.assertFalse(var.isOptional());

    }


    @Test
    public void testStartAfterBefore() {
        ModelDispatcher model = makeModelDispatcher();
        IntervalVar var = model.intervalVar(0, 10, 5, true);
        model.add(startAfter(var, 5));
        ConcreteCPModel cp1 = model.cpInstantiate();



    }

}
