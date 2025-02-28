package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.util.ImmutableSet;

import java.util.Collection;
import java.util.function.Function;

public record And(ImmutableSet<BoolExpression> exprs) implements SymbolicBoolExpression, NonLeafExpressionNode {

    public And(BoolExpression[] exprs) {
        this(ImmutableSet.of(exprs));
    }

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return exprs;
    }

    @Override
    public boolean defaultEvaluateBool() throws VariableNotFixedException {
        for (BoolExpression expr : exprs)
            if (!expr.evaluateBool()) return false;
        return true;
    }

    @Override
    public IntExpression mapSubexpressions(Function<Expression, Expression> f) {
        return new And(exprs.stream().map(x -> (BoolExpression) f.apply(x)).collect(ImmutableSet.toImmutableSet()));
    }

    @Override
    public String toString() {
        return show();
    }
}
