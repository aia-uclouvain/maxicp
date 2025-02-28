package org.maxicp.modeling.algebra.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;

import static org.maxicp.modeling.Factory.plus;

/**
 * Sum of two Cumul Functions.
 *
 * @author Pierre Schaus, Charles Thomas, Augustin Delecluse
 */
public record PlusCumulFunction(CumulFunction left, CumulFunction right) implements CumulFunction {
    @Override
    public IntExpression heightAtStart(IntervalVar interval) {
        if (!inScope(interval)) {
            throw new IllegalArgumentException("this interval is not present in the function");
        } else {
            if (!left.inScope(interval)) {
                return right.heightAtStart(interval);
            } else if (!right.inScope(interval)) {
                return left.heightAtStart(interval);
            } else {
                IntExpression leftHeight = left.heightAtStart(interval);
                IntExpression rightHeight = right.heightAtStart(interval);
                return plus(leftHeight, rightHeight);
            }
        }
    }

    @Override
    public IntExpression heightAtEnd(IntervalVar interval) {
        if (!inScope(interval)) {
            throw new IllegalArgumentException("this interval is not present in the function");
        } else {
            if (!left.inScope(interval)) {
                return right.heightAtEnd(interval);
            } else if (!right.inScope(interval)) {
                return left.heightAtEnd(interval);
            } else {
                IntExpression leftHeight = left.heightAtEnd(interval);
                IntExpression rightHeight = right.heightAtEnd(interval);
                return plus(leftHeight, rightHeight);
            }
        }
    }

    @Override
    public boolean inScope(IntervalVar interval) {
        return left.inScope(interval) || right.inScope(interval);
    }
}
