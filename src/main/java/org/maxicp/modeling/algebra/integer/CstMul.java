package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record CstMul(IntExpression expr, int mul) implements SymbolicIntExpression, NonLeafExpressionNode {
    public CstMul {
        // ensures mul is always a positive number
        if(mul < 0) {
            mul = -mul;
            expr = new UnaryMinus(expr);
        }
    }

    @Override
    public Collection<Expression> computeSubexpressions() {
        return List.of(expr);
    }

    @Override
    public CstMul mapSubexpressions(Function<Expression, Expression> f) {
        return new CstMul((IntExpression) f.apply(expr), mul);
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        if(mul != 0)
            return expr().evaluate() * mul;
        return 0;
    }

    @Override
    public int defaultMin() {
        if(mul != 0)
            return expr().min() * mul;
        return 0;
    }

    @Override
    public int defaultMax() {
        if(mul != 0)
            return expr().max() * mul;
        return 0;
    }

    @Override
    public boolean defaultContains(int val) {
        if(mul == 0) {
            return val == 0;
        }

        if(val % mul != 0)
            return false;
        return expr.contains(val / mul);
    }

    @Override
    public int defaultFillArray(int[] array) {
        if(mul == 0) {
            array[0] = 0;
            return 1;
        }

        int v = expr.fillArray(array);
        for (int i = 0; i < v; i++)
            array[i] = array[i]*mul;
        return v;
    }

    @Override
    public int defaultSize() {
        if(mul == 0)
            return 1;
        return expr.size();
    }

    @Override
    public String toString() {
        return show();
    }
}
