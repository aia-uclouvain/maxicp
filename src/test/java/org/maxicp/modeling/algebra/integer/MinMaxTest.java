package org.maxicp.modeling.algebra.integer;

import org.junit.Test;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import static org.junit.Assert.*;

public class MinMaxTest {
    @Test
    public void evaluateOK1() throws VariableNotFixedException {
        ModelDispatcher baseModel = Factory.makeModelDispatcher();

        IntVar a = baseModel.intVar(10, 10);
        IntVar b = baseModel.intVar(11, 1000);

        IntExpression min1 = Factory.min(a, b);
        assertEquals(min1.evaluate(), 10);

        IntExpression min2 = Factory.min(b, a);
        assertEquals(min2.evaluate(), 10);
    }

    @Test
    public void evaluateOK2() throws VariableNotFixedException {
        ModelDispatcher baseModel = Factory.makeModelDispatcher();

        IntVar a = baseModel.intVar(10, 10);
        IntVar b = baseModel.intVar(10, 1000);

        IntExpression min1 = Factory.min(a, b);
        assertEquals(min1.evaluate(), 10);

        IntExpression min2 = Factory.min(b, a);
        assertEquals(min2.evaluate(), 10);
    }

    @Test
    public void evaluateKO1() {
        ModelDispatcher baseModel = Factory.makeModelDispatcher();

        IntVar a = baseModel.intVar(10, 11);
        IntVar b = baseModel.intVar(10, 1000);

        IntExpression min1 = Factory.min(a, b);
        assertThrows(VariableNotFixedException.class, min1::evaluate);

        IntExpression min2 = Factory.min(b, a);
        assertThrows(VariableNotFixedException.class, min1::evaluate);
    }

    @Test
    public void evaluateKO2() {
        ModelDispatcher baseModel = Factory.makeModelDispatcher();

        IntVar a = baseModel.intVar(10, 11);
        IntVar b = baseModel.intVar(11, 1000);

        IntExpression min1 = Factory.min(a, b);
        assertThrows(VariableNotFixedException.class, min1::evaluate);

        IntExpression min2 = Factory.min(b, a);
        assertThrows(VariableNotFixedException.class, min1::evaluate);
    }
}
