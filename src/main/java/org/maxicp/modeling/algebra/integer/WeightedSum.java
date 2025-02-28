package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.util.ImmutableSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

public record WeightedSum(IntExpression[] subexprs, int[] weights) implements SymbolicIntExpression, NonLeafExpressionNode {

    public WeightedSum {
        if(subexprs.length == 0 || subexprs.length != weights.length)
            throw new IllegalArgumentException();
    }

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return ImmutableSet.of(subexprs);
    }

    @Override
    public WeightedSum mapSubexpressions(Function<Expression, Expression> f) {
        return new WeightedSum(Arrays.stream(subexprs).map(f).map(x -> (IntExpression)x).toArray(IntExpression[]::new), weights);
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        int c = 0;
        for(int i = 0; i < subexprs.length; i++)
            c += subexprs[i].evaluate() * weights[i];
        return c;
    }

    @Override
    public int defaultMin() {
        int c = 0;
        for(int i = 0; i < subexprs.length; i++) {
            if(weights[i] < 0)
                c += subexprs[i].max() * weights[i];
            else
                c += subexprs[i].min() * weights[i];
        }
        return c;
    }

    @Override
    public int defaultMax() {
        int c = 0;
        for(int i = 0; i < subexprs.length; i++) {
            if(weights[i] < 0)
                c += subexprs[i].min() * weights[i];
            else
                c += subexprs[i].max() * weights[i];
        }
        return c;
    }

    @Override
    public String toString() {
        return show();
    }
}