package org.maxicp.modeling.concrete;

import org.junit.Test;
import org.maxicp.cp.modeling.CPModelInstantiator;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.bool.LessOrEq;
import static org.junit.Assert.*;

public class FixPointTest {
    @Test
    public void test1() {
        ModelDispatcher baseModel = Factory.makeModelDispatcher();
        baseModel.cpInstantiate();

        IntVar a = baseModel.intVar(0, 10);
        IntVar b = baseModel.intVar(0, 10);

        baseModel.add(new LessOrEq(Factory.plus(a, b), 3), false);
        assertEquals(10, a.max());
        assertEquals(10, b.max());
        baseModel.add(new LessOrEq(Factory.plus(a, b), 3), true);
        assertNotEquals(10, a.max());
        assertNotEquals(10, b.max());
        System.out.println(a.max());
    }
}
