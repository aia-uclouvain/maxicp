package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record CstOffset(IntExpression expr, int v) implements SymbolicIntExpression, NonLeafExpressionNode {
    @Override
    public Collection<Expression> computeSubexpressions() {
        return List.of(expr);
    }

    @Override
    public CstOffset mapSubexpressions(Function<Expression, Expression> f) {
        return new CstOffset((IntExpression) f.apply(expr), v);
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        return expr().evaluate() + v;
    }

    @Override
    public int defaultMin() {
        return expr().min() + v;
    }

    @Override
    public int defaultMax() {
        return expr().max() + v;
    }

    @Override
    public boolean defaultContains(int val) {
        return expr.contains(val - v);
    }

    @Override
    public int defaultFillArray(int[] array) {
        int v = expr.fillArray(array);
        for (int i = 0; i < v; i++)
            array[i] = array[i]+v;
        return v;
    }

    @Override
    public int defaultSize() {
        return expr.size();
    }

    @Override
    public String toString() {
        return show();
    }
}
