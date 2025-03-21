package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record Not(BoolExpression a) implements SymbolicBoolExpression, NonLeafExpressionNode {
    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(a);
    }

    @Override
    public boolean defaultEvaluateBool() throws VariableNotFixedException {
        return !a.evaluateBool();
    }

    @Override
    public IntExpression mapSubexpressions(Function<Expression, Expression> f) {
        return new Not((BoolExpression) f.apply(a));
    }

    @Override
    public String toString() {
        return show();
    }
}
