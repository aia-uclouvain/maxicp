package org.maxicp.cp.modeling;

import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.algebra.integer.Element1D;
import org.maxicp.modeling.algebra.integer.IntExpression;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressionInstantiationTest {
    @Test
    void expressionInstantiation() {
        ModelDispatcher modelDispatcher = new ModelDispatcher();

        int[] array = new int[]{10, 0, 10};
        IntExpression index = modelDispatcher.intVar(0, 2);

        modelDispatcher.add(Factory.neq(1, index));

        IntExpression element = new Element1D(array, index);
        assertEquals(0, element.min());
        assertEquals(10, element.max());

        modelDispatcher.cpInstantiate();
        assertEquals(10, element.min());
        assertEquals(10, element.max());
    }
}
