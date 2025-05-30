package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;

public record Minimization(IntExpression expr) implements Objective {
    @Override
    public ModelProxy getModelProxy() {
        return expr.getModelProxy();
    }

    @Override
    public int evaluate() throws VariableNotFixedException {
        return expr.evaluate();
    }
}
