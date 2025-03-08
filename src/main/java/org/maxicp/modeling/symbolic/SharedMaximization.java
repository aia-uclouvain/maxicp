package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.concurrent.atomic.AtomicInteger;

public class SharedMaximization implements Objective {

    public final IntExpression expr;
    public final AtomicInteger bound;

    public SharedMaximization(IntExpression expr, int initialValue) {
        this.expr = expr;
        bound = new AtomicInteger(initialValue);
    }

    public SharedMaximization(IntExpression expr) {
        this(expr, Integer.MIN_VALUE+1);
    }

    @Override
    public ModelProxy getModelProxy() {
        return expr.getModelProxy();
    }
}
