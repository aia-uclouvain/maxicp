package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record StartAfter(IntervalVar interval, IntExpression value) implements SymbolicBoolExpression {

    @Override
    public boolean defaultEvaluateBool() throws VariableNotFixedException {
        if (isTriviallyTrue())
            return true;
        if (isTriviallyFalse())
            return false;
        throw new VariableNotFixedException();
    }

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(interval, value);
    }

    @Override
    public IntExpression mapSubexpressions(Function<Expression, Expression> f) {
        return new StartAfter((IntervalVar) f.apply(interval), (IntExpression) f.apply(value));
    }

    @Override
    public boolean isFixed() {
        if(getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).isFixed();
        if (isTriviallyFalse() || isTriviallyTrue())
            return true;
        return size() == 1;
    }

    private boolean isTriviallyTrue() {
        return interval.isPresent() && interval.startMin() > value.max();
    }

    private boolean isTriviallyFalse() {
        return interval.isPresent() && interval.startMax() <= value.min();
    }


    @Override
    public ModelProxy getModelProxy() {
        return interval.getModelProxy();
    }
}
