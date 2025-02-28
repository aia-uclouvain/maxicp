package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.Constant;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record Eq(IntExpression a, IntExpression b) implements SymbolicBoolExpression, NonLeafExpressionNode {
    public Eq(IntExpression a, int b) {
        this(a, new Constant(a.getModelProxy(), b));
    }

    public Eq(int a, IntExpression b) {
        this(new Constant(b.getModelProxy(), a), b);
    }

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(a, b);
    }

    @Override
    public boolean defaultEvaluateBool() throws VariableNotFixedException {
        return a.evaluate() == b.evaluate();
    }


    @Override
    public IntExpression mapSubexpressions(Function<Expression, Expression> f) {
        return new Eq((IntExpression) f.apply(a), (IntExpression) f.apply(b));
    }

    @Override
    public String toString() {
        return show();
    }
}
