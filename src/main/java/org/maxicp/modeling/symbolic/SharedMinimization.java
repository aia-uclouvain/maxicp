package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.concurrent.atomic.AtomicInteger;

public class SharedMinimization implements Objective {

    public final IntExpression expr;
    public final AtomicInteger bound;

    public SharedMinimization(IntExpression expr, int initialValue) {
        this.expr = expr;
        bound = new AtomicInteger(initialValue);
    }

    public SharedMinimization(IntExpression expr) {
        this(expr, Integer.MAX_VALUE-1);
    }

    @Override
    public ModelProxy getModelProxy() {
        return expr.getModelProxy();
    }

    @Override
    public int evaluate() throws VariableNotFixedException {
        return expr.evaluate();
    }
}
