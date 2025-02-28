/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.algebra.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;

import static org.maxicp.modeling.Factory.*;

/**
 * Difference between two Cumul Functions.
 *
 * @author Pierre Schaus, Charles Thomas, Augustin Delecluse
 */
public record MinusCumulFunction(CumulFunction left, CumulFunction right) implements CumulFunction {

    @Override
    public boolean inScope(IntervalVar interval) {
        return left.inScope(interval) || right.inScope(interval);
    }

    @Override
    public IntExpression heightAtStart(IntervalVar interval) {
        if (!inScope(interval)) {
            throw new IllegalArgumentException("this interval is not present in the function");
        } else {
            if (!left.inScope(interval)) {
                return minus(right.heightAtStart(interval));
            } else if (!right.inScope(interval)) {
                return left.heightAtStart(interval);
            } else {
                IntExpression leftHeight = left.heightAtStart(interval);
                IntExpression rightHeight = right.heightAtStart(interval);
                return sum(leftHeight, minus(rightHeight));
            }
        }
    }

    public IntExpression heightAtEnd(IntervalVar interval) {
        if (!inScope(interval)) {
            throw new IllegalArgumentException("this interval is not present in the function");
        } else {
            if (!left.inScope(interval)) {
                return minus(right.heightAtEnd(interval));
            } else if (!right.inScope(interval)) {
                return left.heightAtEnd(interval);
            } else {
                IntExpression leftHeight = left.heightAtEnd(interval);
                IntExpression rightHeight = right.heightAtEnd(interval);
                return sum(leftHeight, minus(rightHeight));
            }
        }
    }
}