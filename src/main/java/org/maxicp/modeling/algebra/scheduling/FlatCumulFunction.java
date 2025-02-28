package org.maxicp.modeling.algebra.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;

/**
 * Flat Elementary Cumul Function
 *
 * @author Pierre Schaus, Charles Thomas, Augustin Delecluse
 */
public record FlatCumulFunction() implements CumulFunction {
    @Override
    public IntExpression heightAtStart(IntervalVar interval) {
        throw new UnsupportedOperationException("no interval present");
    }

    @Override
    public IntExpression heightAtEnd(IntervalVar interval) {
        throw new UnsupportedOperationException("no interval present");
    }

    @Override
    public boolean inScope(IntervalVar interval) {
        return false;
    }
}
