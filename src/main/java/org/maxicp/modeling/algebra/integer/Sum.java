package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record Sum(IntExpression... subexprs) implements SymbolicIntExpression, NonLeafExpressionNode {

    public Sum {
        if (subexprs.length == 0)
            throw new IllegalArgumentException();
    }

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(subexprs);
    }

    @Override
    public Sum mapSubexpressions(Function<Expression, Expression> f) {
        return new Sum(Arrays.stream(subexprs).map(f).map(x -> (IntExpression) x).toArray(IntExpression[]::new));
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        int c = 0;
        for (IntExpression expr : subexprs)
            c += expr.evaluate();
        return c;
    }

    @Override
    public int defaultMin() {
        int c = 0;
        for (IntExpression expr : subexprs)
            c += expr.min();
        return c;
    }

    @Override
    public int defaultMax() {
        int c = 0;
        for (IntExpression expr : subexprs)
            c += expr.max();
        return c;
    }

    @Override
    public String toString() {
        return show();
    }
}