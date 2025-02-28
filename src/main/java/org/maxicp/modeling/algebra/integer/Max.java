package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record Max(IntExpression[] exprs) implements SymbolicIntExpression, NonLeafExpressionNode {
    @Override
    public Collection<Expression> computeSubexpressions() {
        return List.of(exprs);
    }

    @Override
    public Max mapSubexpressions(Function<Expression, Expression> f) {
        return new Max(Arrays.stream(exprs).map(x -> (IntExpression) f.apply(x)).toArray(IntExpression[]::new));
    }

    @Override
    public boolean isFixed() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).isFixed();
        return NonLeafExpressionNode.super.isFixed();
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        int max = Integer.MIN_VALUE;
        boolean isMaxKnown = false;
        for (IntExpression expr : exprs) {
            try {
                int thisMax = expr.evaluate();
                if (thisMax >= max) {
                    max = thisMax;
                    isMaxKnown = true;
                }
            } catch (VariableNotFixedException e) {
                int thisMax = expr.max();
                if (thisMax > max) {
                    max = thisMax;
                    isMaxKnown = false;
                }
            }
        }
        if (isMaxKnown)
            return max;
        else
            throw new VariableNotFixedException();
    }

    @Override
    public int defaultMin() {
        int max = Integer.MIN_VALUE;
        for(IntExpression expr: exprs)
            max = Math.max(max, expr.min());
        return max;
    }

    @Override
    public int defaultMax() {
        int max = Integer.MIN_VALUE;
        for (IntExpression expr : exprs)
            max = Math.max(max, expr.max());
        return max;
    }

    @Override
    public String toString() {
        return show();
    }
}
