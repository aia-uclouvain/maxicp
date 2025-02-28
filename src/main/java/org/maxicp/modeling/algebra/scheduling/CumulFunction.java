/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.algebra.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;

/**
 * Represents a Cumulative Function as described in
 * <p><i>Reasoning with conditional time-intervals. part ii: An algebraical model for resources</i>
 * Laborie, P., Rogerie, J., Shaw, P., Vilím, P
 * <p>See <a href="https://cdn.aaai.org/ocs/60/60-2374-1-PB.pdf">The article.</a>
 *
 * @author Pierre Schaus, Charles Thomas, Augustin Delecluse
 */
public interface CumulFunction {
    /**
     * Returns an {@link IntExpression} representing the height of the cumul function at the start of the
     * interval variable given in parameter.
     *
     * @param interval an {@link IntervalVar}
     * @return an int expression representing the height of the cumul function at the start of interval
     */
    public IntExpression heightAtStart(IntervalVar interval);

    /**
     * Returns an {@link IntExpression} representing the height of the cumul function at the end of the
     * interval variable given in parameter.
     *
     * @param interval an {@link IntervalVar}
     * @return an int expression representing the height of the cumul function at the end of interval
     */
    public IntExpression heightAtEnd(IntervalVar interval);

    /**
     * Returns whether the interval var given in parameter contributes to the cumul function
     * @param interval an {@link IntervalVar}
     * @return a boolean that indicates if interval contributes to the cumul function.
     */
    public boolean inScope(IntervalVar interval);
}
