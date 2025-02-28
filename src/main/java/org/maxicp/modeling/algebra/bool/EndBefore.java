package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.Constant;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.util.exception.NotYetImplementedException;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record EndBefore(IntervalVar intervalVar, IntExpression end) implements SymbolicBoolExpression, NonLeafExpressionNode {

    public EndBefore(IntervalVar intervalVar, int end) {
        this(intervalVar, new Constant(intervalVar.getModelProxy(), end));
    }
    
    @Override
    public boolean defaultEvaluateBool() throws VariableNotFixedException {
        if (isTriviallyTrue())
            return true;
        if (isTriviallyFalse())
            return false;
        throw new VariableNotFixedException();
    }

    private boolean isTriviallyTrue() { // end(a) < end
        return intervalVar.isPresent() && intervalVar.endMax() < end.min();
    }

    private boolean isTriviallyFalse() { // end(a) >= end
        return intervalVar.isPresent() && intervalVar.endMin() >= end.max();
    }

    @Override
    public boolean isFixed() {
        if(getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).isFixed();
        if (isTriviallyFalse() || isTriviallyTrue())
            return true;
        return size() == 1;
    }

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(intervalVar, end);
    }

    @Override
    public IntExpression mapSubexpressions(Function<Expression, Expression> f) {
        throw new NotYetImplementedException("Not implemented yet.");
    }
}
