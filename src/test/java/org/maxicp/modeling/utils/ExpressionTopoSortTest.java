package org.maxicp.modeling.utils;

import org.junit.Test;
import org.maxicp.modeling.Factory;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ExpressionTopoSortTest {
    @Test
    public void depth4() {
        ModelDispatcher baseModel = Factory.makeModelDispatcher();

        //first layer
        IntExpression l1_1 = baseModel.intVar(10, 20);
        IntExpression l1_2 = baseModel.constant(12);
        IntExpression l1_3 = Factory.sum(baseModel.intVar(3, 5), baseModel.constant(6));

        //second layer
        IntExpression l2_1 = Factory.sum(l1_1, l1_2);
        IntExpression l2_2 = Factory.minus(l1_2, l1_3);

        //third layer
        IntExpression l3_1 = Factory.sum(l2_2, l1_1);
        IntExpression l3_2 = Factory.minus(l2_1, l1_2);

        //fourth layer
        IntExpression l4_1 = Factory.sum(l3_2, l3_1, l2_2, l1_3);

        Set<IntExpression> exprs = Set.of(l1_2, l2_2, l3_2, l4_1, l3_1, l2_1, l1_1, l1_3);
        List<List<IntExpression>> toposorted = ExpressionsTopoSort.toposort(exprs);

        assertEquals(4, toposorted.size());
        assertEquals(Set.copyOf(toposorted.get(0)), Set.of(l1_1, l1_2, l1_3));
        assertEquals(Set.copyOf(toposorted.get(1)), Set.of(l2_1, l2_2));
        assertEquals(Set.copyOf(toposorted.get(2)), Set.of(l3_1, l3_2));
        assertEquals(Set.copyOf(toposorted.get(3)), Set.of(l4_1));
    }

    @Test
    public void linear() {
        ModelDispatcher baseModel = Factory.makeModelDispatcher();
        IntExpression expr = baseModel.intVar(10, 20);
        Set<IntExpression> exprs = new HashSet<>();
        exprs.add(expr);
        for(int i = 0; i < 10; i++) {
            expr = Factory.sum(expr, expr);
            exprs.add(expr);
        }
        List<List<IntExpression>> toposorted = ExpressionsTopoSort.toposort(exprs);
        assertEquals(11, toposorted.size());
    }
}
