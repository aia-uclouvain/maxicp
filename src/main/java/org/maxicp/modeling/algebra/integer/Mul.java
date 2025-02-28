package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record Mul(IntExpression... subexprs) implements SymbolicIntExpression, NonLeafExpressionNode {
    public Mul {
        if (subexprs.length == 0)
            throw new IllegalArgumentException();
    }

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(subexprs);
    }

    @Override
    public Mul mapSubexpressions(Function<Expression, Expression> f) {
        return new Mul(Arrays.stream(subexprs).map(f).map(x -> (IntExpression) x).toArray(IntExpression[]::new));
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        int c = 1;
        for (IntExpression expr : subexprs)
            c *= expr.evaluate();
        return c;
    }

    private int[] defaultMinMax(){
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (IntExpression expr : subexprs) {
            int nmin = expr.min();
            int nmax = expr.max();
            int a = min * nmin;
            int b = max * nmax;
            int c = min * nmax;
            int d = max * nmin;
            min = Math.min(Math.min(a, b), Math.min(c, d));
            max = Math.max(Math.max(a, b), Math.max(c, d));
        }
        return new int[]{min, max};
    }

    @Override
    public int defaultMin() {
        return defaultMinMax()[0];
    }

    @Override
    public int defaultMax() {
        return defaultMinMax()[1];
    }

    @Override
    public String toString() {
        return show();
    }
}