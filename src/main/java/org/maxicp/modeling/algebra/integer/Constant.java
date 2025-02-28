package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record Constant(ModelProxy modelProxy, int v) implements SymbolicIntExpression {
    @Override
    public Collection<Expression> computeSubexpressions() {
        return List.of();
    }

    @Override
    public Constant mapSubexpressions(Function<Expression, Expression> f) {
        return this;
    }

    @Override
    public boolean isFixed() {
        return true;
    }

    @Override
    public ModelProxy getModelProxy() {
        return modelProxy;
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        return v;
    }

    @Override
    public int defaultMin() {
        return v;
    }

    @Override
    public int defaultMax() {
        return v;
    }

    @Override
    public boolean defaultContains(int vv) {
        return vv == v;
    }

    @Override
    public int defaultFillArray(int[] array) {
        array[0] = v;
        return 1;
    }

    @Override
    public int defaultSize() {
        return 1;
    }

    @Override
    public String toString() {
        return show();
    }
}
