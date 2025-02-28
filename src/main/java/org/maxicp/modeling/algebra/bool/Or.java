package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.util.ImmutableSet;

import java.util.Collection;
import java.util.function.Function;

public record Or(ImmutableSet<BoolExpression> exprs) implements SymbolicBoolExpression, NonLeafExpressionNode {
    public Or(BoolExpression[] exprs) { this(ImmutableSet.of(exprs)); }

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return exprs;
    }

    @Override
    public boolean defaultEvaluateBool() throws VariableNotFixedException {
        boolean atLeastOneNotFixed = false;
        for(BoolExpression b: exprs) {
            if(!b.isFixed()) {
                atLeastOneNotFixed = true;
            }
            else if(b.evaluateBool())
                return true;
        }
        if(atLeastOneNotFixed)
            throw new VariableNotFixedException();
        return false;
    }

    @Override
    public boolean isFixed() {
        boolean atLeastOneUnfixed = false;
        for(BoolExpression b: exprs) {
            if(b.isFixed() && b.min() == 1) //calling min avoids the VariableNotFixedException and its useless try/catch
                return true;
            if(!b.isFixed())
                atLeastOneUnfixed = true;
        }
        return !atLeastOneUnfixed;
    }


    @Override
    public IntExpression mapSubexpressions(Function<Expression, Expression> f) {
        return new Or(exprs.stream().map(x -> (BoolExpression)f.apply(x)).collect(ImmutableSet.toImmutableSet()));
    }

    @Override
    public String toString() {
        return show();
    }
}
