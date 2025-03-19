package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.util.exception.NotYetImplementedException;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Gives the start of an {@link IntervalVar}
 * @param interval the interval, it must be present
 */
public record IntervalStart(IntervalVar interval) implements SymbolicIntExpression {

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        if (!interval.isPresent())
            throw new RuntimeException("Interval must be present");
        else if (interval.startMin() != interval.startMax())
            throw new VariableNotFixedException();
        return interval.startMin();
    }


    @Override
    public int defaultMin() {
        if (!interval.isPresent())
            throw new RuntimeException("Interval must be present");
        return interval.startMin();
    }

    @Override
    public int defaultMax() {
        if (!interval.isPresent())
            throw new RuntimeException("Interval must be present");
        return interval.startMax();
    }

    @Override
    public boolean defaultContains(int v) {
        if (!interval.isPresent())
            throw new RuntimeException("Interval must be present");
        return intervalStartContains(v);
    }

    @Override
    public int defaultFillArray(int[] array) {
        if (!interval.isPresent())
            throw new RuntimeException("Interval must be present");
        else {
            int i = 0;
            int startMin = interval.startMin();
            int startMax = interval.startMax();
            // only add value if optional and not already contained in endMin...endMax
            for (int v = startMin; v <= startMax; v++) {
                array[i++] = v;
            }
            return i;
        }
    }

    private boolean intervalStartContains(int v) {
        return interval.startMin() <= v && v <= interval.startMax();
    }

    @Override
    public int defaultSize() {
        if (!interval.isPresent())
            throw new RuntimeException("Interval must be present");
        else {
            return interval.startMax() - interval.startMin() + 1;
        }
    }

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(interval);
    }

    @Override
    public IntExpression mapSubexpressions(Function<Expression, Expression> f) {
        throw new NotYetImplementedException("not implemented");
    }

    @Override
    public boolean isFixed() {
        return size() == 1;
    }

    @Override
    public ModelProxy getModelProxy() {
        return interval.getModelProxy();
    }
}
