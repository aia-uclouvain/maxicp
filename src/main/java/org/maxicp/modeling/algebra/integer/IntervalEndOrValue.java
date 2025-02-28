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
 * Gives the end or an {@link IntervalVar} or a given value if the interval is not present
 * @param interval
 * @param value
 */
public record IntervalEndOrValue(IntervalVar interval, int value) implements SymbolicIntExpression {

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        if (interval.isOptional())
            throw new VariableNotFixedException();
        if (interval.isPresent()) {
            if (interval.endMin() != interval.endMax())
                throw new VariableNotFixedException();
            return interval.endMax();
        }
        assert interval.isAbsent();
        return value;
    }

    @Override
    public int defaultMin() {
        if (interval.isOptional())
            return Math.min(interval.endMin(), value);
        if (interval.isAbsent())
            return value;
        assert (interval.isPresent());
        return interval.endMin();
    }

    @Override
    public int defaultMax() {
        if (interval.isOptional())
            return Math.max(interval.endMax(), value);
        if (interval.isAbsent())
            return value;
        assert (interval.isPresent());
        return interval.endMax();
    }

    @Override
    public boolean defaultContains(int v) {
        if (interval.isOptional())
            return v == value || intervalEndContains(v);
        if (interval.isAbsent())
            return v == value;
        assert (interval.isPresent());
        return intervalEndContains(v);
    }

    @Override
    public int defaultFillArray(int[] array) {
        if (interval.isAbsent()) {
            array[0] = value;
            return 1;
        } else {
            int i = 0;
            int endMin = interval.endMin();
            int endMax = interval.endMax();
            // only add value if optional and not already contained in endMin...endMax
            if (interval.isOptional() && (value < endMin || value > endMax))
                array[i++] = value;
            for (int v = endMin; v <= endMax; v++) {
                array[i++] = v;
            }
            return i;
        }
    }

    private boolean intervalEndContains(int v) {
        return interval.endMin() <= v && v <= interval.endMax();
    }

    @Override
    public int defaultSize() {
        if (interval.isAbsent())
            return 1;
        if (interval.isOptional()) {
            if (intervalEndContains(value))
                return interval.endMax() - interval.endMin() + 1;
            return interval.endMax() - interval.endMin() + 2;
        }
        return interval.endMax() - interval.endMin() + 1;
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
