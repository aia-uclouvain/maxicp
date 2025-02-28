package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record Present(IntervalVar interval) implements SymbolicBoolExpression {

    @Override
    public boolean defaultEvaluateBool() throws VariableNotFixedException {
        if (interval.isPresent())
            return true;
        if (interval.isAbsent())
            return false;
        throw new VariableNotFixedException();
    }

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(interval);
    }

    @Override
    public IntExpression mapSubexpressions(Function<Expression, Expression> f) {
        return new Present((IntervalVar) f.apply(interval));
    }

    @Override
    public boolean isFixed() {
        if(getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).isFixed();
        if (interval.isPresent() || interval.isAbsent())
            return true;
        return size() == 1;
    }

    @Override
    public ModelProxy getModelProxy() {
        return interval.getModelProxy();
    }

}
