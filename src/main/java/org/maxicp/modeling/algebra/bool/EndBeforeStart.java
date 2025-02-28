package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record EndBeforeStart(IntervalVar a, IntervalVar b) implements SymbolicBoolExpression {

    @Override
    public boolean defaultEvaluateBool() throws VariableNotFixedException {
        if (a.isPresent() && b.isPresent()) {
            if (a.endMax() <= b.startMin())
                return true;
            if (b.startMax() < a.endMin())
                return false;
        }
        throw new VariableNotFixedException();
    }

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(a, b);
    }

    @Override
    public IntExpression mapSubexpressions(Function<Expression, Expression> f) {
        return null;
    }

    private boolean isTriviallyTrue() { // end(a) <= start(b)
        return a.isPresent() && b.isPresent() && a.endMax() <= b.startMin();
    }

    private boolean isTriviallyFalse() { // end(a) > start(b) <-> start(b) < end(a)
        return a.isPresent() && b.isPresent() && b.startMax() < a.endMin();
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
    public ModelProxy getModelProxy() {
        return a.getModelProxy();
    }

    @Override
    public String toString() {
        return show();
    }
}
