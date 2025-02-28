/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;

import java.util.List;

/**
 * CP implementation of a Cumulative Function
 *
 * @author Pierre Schaus, Charles Thomas, Augustin Delecluse
 */
public interface CPCumulFunction extends CumulFunction {
    /**
     * Returns a list of all the activities contributing to this cumulative function. Transformations are applied to the
     * activities following the internal AST of the cumul function.
     * @param positive A boolean indicating the sign to be applied when flattening the cumul function
     * @return A list of all the flattened activities contributing to this cumulative function.
     */
    List<Activity> flatten(boolean positive);

    @Override
    CPIntVar heightAtStart(IntervalVar interval);

    @Override
    CPIntVar heightAtEnd(IntervalVar interval);

    @Override
    boolean inScope(IntervalVar interval);
}
