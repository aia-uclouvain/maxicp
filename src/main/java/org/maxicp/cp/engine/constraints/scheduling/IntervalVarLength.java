/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Constraint linking the length (duration) of an interval variable {@code var} to an integer variable {@code length}.
 *
 * @author Charles Thomas
 */
public class IntervalVarLength extends AbstractCPConstraint {
    CPIntervalVar var;
    CPIntVar length;

    /**
     * Creates a constraint linking {@code length(var)} to {@code length}.
     *
     * @param var    the interval variable
     * @param length the integer variable representing the length of {@code var}
     */
    public IntervalVarLength(CPIntervalVar var, CPIntVar length) {
        super(var.getSolver());
        this.var = var;
        this.length = length;
    }

    @Override
    public void post() {
        var.propagateOnChange(this);
        length.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        length.removeBelow(var.lengthMin());
        length.removeAbove(var.lengthMax());
        var.setLengthMin(length.min());
        var.setLengthMax(length.max());
    }
}