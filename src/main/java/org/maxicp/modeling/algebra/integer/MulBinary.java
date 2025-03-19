package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.bool.BoolExpression;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record MulBinary(IntExpression x, BoolExpression b) implements SymbolicIntExpression, NonLeafExpressionNode {

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(x,b);
    }

    @Override
    public MulBinary mapSubexpressions(Function<Expression, Expression> f) {
        return null;
        // return new MulBinary(Arrays.stream(subexprs).map(f).map(x -> (IntExpression) x).toArray(IntExpression[]::new));
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        return x.evaluate() * b.evaluate();
    }

    @Override
    public int defaultMin() {
        return Math.min(b.min() * x.min(), x.min());
    }

    @Override
    public int defaultMax() {
        return b.max() * x.max();
    }

    @Override
    public String toString() {
        return show();
    }
}