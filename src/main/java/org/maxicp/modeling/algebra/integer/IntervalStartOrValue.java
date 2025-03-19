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
 * Gives the start of an {@link IntervalVar} or a given value if the interval is not present
 * @param interval the interval
 * @param value the default value if the interval is absent
 */
public record IntervalStartOrValue(IntervalVar interval, int value) implements SymbolicIntExpression {

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        if (interval.isOptional())
            throw new VariableNotFixedException();
        if (interval.isPresent()) {
            if (interval.startMin() != interval.startMax())
                throw new VariableNotFixedException();
            return interval.startMax();
        }
        assert interval.isAbsent();
        return value;
    }

    @Override
    public int defaultMin() {
        if (interval.isOptional())
            return Math.min(interval.startMin(), value);
        if (interval.isAbsent())
            return value;
        assert (interval.isPresent());
        return interval.startMin();
    }

    @Override
    public int defaultMax() {
        if (interval.isOptional())
            return Math.max(interval.startMax(), value);
        if (interval.isAbsent())
            return value;
        assert (interval.isPresent());
        return interval.startMax();
    }

    @Override
    public boolean defaultContains(int v) {
        if (interval.isOptional())
            return v == value || intervalStartContains(v);
        if (interval.isAbsent())
            return v == value;
        assert (interval.isPresent());
        return intervalStartContains(v);
    }

    @Override
    public int defaultFillArray(int[] array) {
        if (interval.isAbsent()) {
            array[0] = value;
            return 1;
        } else {
            int i = 0;
            int startMin = interval.startMin();
            int startMax = interval.startMax();
            // only add value if optional and not already contained in endMin...endMax
            if (interval.isOptional() && (value < startMin || value > startMax))
                array[i++] = value;
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
        if (interval.isAbsent())
            return 1;
        if (interval.isOptional()) {
            if (intervalStartContains(value))
                return interval.startMax() - interval.startMin() + 1;
            return interval.startMax() - interval.startMin() + 2;
        }
        return interval.startMax() - interval.startMin() + 1;
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
