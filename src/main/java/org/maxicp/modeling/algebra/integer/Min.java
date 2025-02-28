package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record Min(IntExpression[] exprs) implements SymbolicIntExpression, NonLeafExpressionNode {
    @Override
    public Collection<Expression> computeSubexpressions() {
        return List.of(exprs);
    }

    @Override
    public Min mapSubexpressions(Function<Expression, Expression> f) {
        return new Min(Arrays.stream(exprs).map(x -> (IntExpression) f.apply(x)).toArray(IntExpression[]::new));
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        int min = Integer.MAX_VALUE;
        boolean isMinKnown = false;
        for (IntExpression expr : exprs) {
            try {
                int thisMin = expr.evaluate();
                if (thisMin <= min) {
                    min = thisMin;
                    isMinKnown = true;
                }
            } catch (VariableNotFixedException e) {
                int thisMin = expr.min();
                if (thisMin < min) {
                    min = thisMin;
                    isMinKnown = false;
                }
            }
        }

        if (isMinKnown)
            return min;
        else
            throw new VariableNotFixedException();
    }

    @Override
    public int defaultMin() {
        int min = Integer.MAX_VALUE;
        for (IntExpression expr : exprs)
            min = Math.min(min, expr.min());
        return min;
    }

    @Override
    public int defaultMax() {
        int min = Integer.MAX_VALUE;
        for (IntExpression expr : exprs)
            min = Math.min(min, expr.max());
        return min;
    }

    @Override
    public String toString() {
        return show();
    }
}
