package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.function.Function;

public interface IntExpression extends Expression {
    /**
     * Evaluate this expression. All variables referenced have to be fixed.
     * @throws VariableNotFixedException when a variable is not fixed
     * @return the value of this expression
     */
    int evaluate() throws VariableNotFixedException;

    /**
     * Return a *lower bound* for this expression
     */
    int min();

    /**
     * Return an *upper bound* for this expression
     */
    int max();

    /**
     * Returns whether this expression *can* contain v.
     */
    boolean contains(int v);

    /**
     * Fill an array of minimum size size() with a *superset* of the domain of this expression.
     * Returns `v`, the size of the domain after it has been computed, with {@code v <= size()}.
     */
    int fillArray(int[] array);

    /**
     * *Upper bound* on the size of the domain of this expression.
     */
    int size();

    IntExpression plus(int v);
    IntExpression minus(int v);
    IntExpression times(int v);
    IntExpression plus(IntExpression v);
    IntExpression minus(IntExpression v);
    IntExpression times(IntExpression v);
    IntExpression abs();

    IntExpression mapSubexpressions(Function<Expression, Expression> f);

    /**
     * Gives the current domain of the expression as a human-readable String.
     * If the expression is fixed, a single number is returned.
     * Otherwise, if the domain can be represented as an interval, it is represented by "{min..max}".
     * Otherwise, all values are enumerated in brackets, without any guarantee on the value ordering (i.e. "{v2, v0, v1}")
     *
     * @return representation of the current domain, in human-readable format.
     */
    default String show() {
        if (isFixed()) {
            return String.format("%d", min());
        } else {
            int size = size();
            int min = min();
            int max = max();
            boolean isSparse = size != max - min + 1;
            if (size == 2) {
                // prevent to return {max, min} if we are using a fill array operation intead
                return String.format("{%d, %d}", min, max);
            } else if (isSparse) {
                int[] values = new int[size];
                size = fillArray(values);
                StringBuilder builder = new StringBuilder();
                builder.append('{');
                char separator = ',';
                for (int i = 0 ; i < size ; i++) {
                    int value = values[i];
                    builder.append(value);
                    if (i < size - 1) {
                        builder.append(separator);
                    }
                }
                builder.append('}');
                return builder.toString();
            } else {
                return String.format("{%d..%d}", min, max);
            }
        }
    }
}