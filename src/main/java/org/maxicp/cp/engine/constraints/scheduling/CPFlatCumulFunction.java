/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.modeling.IntervalVar;

import java.util.List;

/**
 * CP implementation of a Flat Cumulative Function
 *
 * @author Pierre Schaus, Charles Thomas, Augustin Delecluse
 */
public class CPFlatCumulFunction implements CPCumulFunction {
    public CPFlatCumulFunction() {
    }

    @Override
    public List<Activity> flatten(boolean positive) {
        return List.of();
    }

    @Override
    public CPIntVar heightAtStart(IntervalVar interval) {
        throw new UnsupportedOperationException("no interval present");
    }

    @Override
    public CPIntVar heightAtEnd(IntervalVar interval) {
        throw new UnsupportedOperationException("no interval present");
    }

    @Override
    public boolean inScope(IntervalVar interval) {
        return false;
    }
}
