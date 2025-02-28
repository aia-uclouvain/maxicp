package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record UnaryMinus(IntExpression expr) implements SymbolicIntExpression, NonLeafExpressionNode {
    @Override
    public Collection<Expression> computeSubexpressions() {
        return List.of(expr);
    }

    @Override
    public UnaryMinus mapSubexpressions(Function<Expression, Expression> f) {
        return new UnaryMinus((IntExpression) f.apply(expr));
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        return -expr.evaluate();
    }

    @Override
    public int defaultMin() {
        return -expr.max();
    }

    @Override
    public int defaultMax() {
        return -expr.min();
    }

    @Override
    public boolean defaultContains(int v) {
        return expr.contains(-v);
    }

    @Override
    public int defaultFillArray(int[] array) {
        int v = expr.fillArray(array);
        for(int i = 0; i < v; i++)
            array[i] = -array[i];
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
