package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.Constant;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record LessOrEq(IntExpression a, IntExpression b) implements SymbolicBoolExpression, NonLeafExpressionNode {
    public LessOrEq(IntExpression a, int b) {
        this(a, new Constant(a.getModelProxy(), b));
    }

    public LessOrEq(int a, IntExpression b) {
        this(new Constant(b.getModelProxy(), a), b);
    }

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(a, b);
    }

    @Override
    public boolean defaultEvaluateBool() throws VariableNotFixedException {
        if(isTriviallyTrue())
            return true;
        if(isTriviallyFalse())
            return false;
        return a.evaluate() <= b.evaluate();
    }

    private boolean isTriviallyTrue() {
        return a.max() <= b.min();
    }

    private boolean isTriviallyFalse() {
        return a.min() > b.max();
    }

    @Override
    public boolean isFixed() {
        if(a.isFixed() && b.isFixed())
            return true;
        return isTriviallyTrue() || isTriviallyFalse();
    }

    @Override
    public IntExpression mapSubexpressions(Function<Expression, Expression> f) {
        return new LessOrEq((IntExpression) f.apply(a), (IntExpression) f.apply(b));
    }

    @Override
    public String toString() {
        return show();
    }
}
