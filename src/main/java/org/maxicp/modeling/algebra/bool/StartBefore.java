package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Boolean variable that represents if an interval is executed before a given value
 * @param interval time interval that must be tested
 * @param value value before which the time interval must occur
 */
public record StartBefore(IntervalVar interval, IntExpression value) implements SymbolicBoolExpression {

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
        return new StartBefore((IntervalVar) f.apply(interval), (IntExpression) f.apply(value));
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
        return interval.isPresent() && interval.startMax() <= value.min();
    }

    private boolean isTriviallyFalse() {
        return interval.isPresent() && interval.startMin() > value.max();
    }


    @Override
    public ModelProxy getModelProxy() {
        return interval.getModelProxy();
    }
}
