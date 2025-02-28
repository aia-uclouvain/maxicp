package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.integer.IntExpression;

public record Maximization(IntExpression expr) implements Objective {
    @Override
    public ModelProxy getModelProxy() {
        return expr.getModelProxy();
    }
}
