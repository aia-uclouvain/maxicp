package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public record InSet(IntExpression a, Set<Integer> b) implements SymbolicBoolExpression, NonLeafExpressionNode {
    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(a);
    }

    @Override
    public boolean defaultEvaluateBool() throws VariableNotFixedException {
        return b.contains(a.evaluate());
    }


    @Override
    public IntExpression mapSubexpressions(Function<Expression, Expression> f) {
        return new InSet((IntExpression) f.apply(a), b);
    }

    @Override
    public String toString() {
        return show();
    }
}
