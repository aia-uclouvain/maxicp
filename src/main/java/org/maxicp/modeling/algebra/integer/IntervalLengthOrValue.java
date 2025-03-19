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
 * Gives the length an {@link IntervalVar} or a given value if the interval is not present
 * @param interval the interval
 * @param value the default value if the interval is absent
 */
public record IntervalLengthOrValue(IntervalVar interval, int value) implements SymbolicIntExpression {

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        if (interval.isOptional())
            throw new VariableNotFixedException();
        if (interval.isPresent()) {
            if (interval.lengthMin() != interval.lengthMax())
                throw new VariableNotFixedException();
            return interval.lengthMax();
        }
        assert interval.isAbsent();
        return value;
    }

    @Override
    public int defaultMin() {
        if (interval.isOptional())
            return Math.min(interval.lengthMin(), value);
        if (interval.isAbsent())
            return value;
        assert (interval.isPresent());
        return interval.lengthMin();
    }

    @Override
    public int defaultMax() {
        if (interval.isOptional())
            return Math.max(interval.lengthMax(), value);
        if (interval.isAbsent())
            return value;
        assert (interval.isPresent());
        return interval.lengthMax();
    }

    @Override
    public boolean defaultContains(int v) {
        if (interval.isOptional())
            return v == value || intervalLengthContains(v);
        if (interval.isAbsent())
            return v == value;
        assert (interval.isPresent());
        return intervalLengthContains(v);
    }

    @Override
    public int defaultFillArray(int[] array) {
        if (interval.isAbsent()) {
            array[0] = value;
            return 1;
        } else {
            int i = 0;
            int lengthMin = interval.lengthMin();
            int lengthMax = interval.lengthMax();
            // only add value if optional and not already contained in lengthMin...lengthMax
            if (interval.isOptional() && (value < lengthMin || value > lengthMax))
                array[i++] = value;
            for (int v = lengthMin; v <= lengthMax; v++) {
                array[i++] = v;
            }
            return i;
        }
    }

    private boolean intervalLengthContains(int v) {
        return interval.lengthMin() <= v && v <= interval.lengthMax();
    }

    @Override
    public int defaultSize() {
        if (interval.isAbsent())
            return 1;
        if (interval.isOptional()) {
            if (intervalLengthContains(value))
                return interval.lengthMax() - interval.lengthMin() + 1;
            return interval.lengthMax() - interval.lengthMin() + 2;
        }
        return interval.lengthMax() - interval.lengthMin() + 1;
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
