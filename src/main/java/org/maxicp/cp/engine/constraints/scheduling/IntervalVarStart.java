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
 * Constraint linking the start time of an interval variable {@code var} to an integer variable {@code start}.
 *
 * @author Pierre Schaus
 */
public class IntervalVarStart extends AbstractCPConstraint {
    CPIntervalVar var;
    CPIntVar start;

    /**
     * Creates a constraint linking {@code start(var)} to {@code start}.
     *
     * @param var   the interval variable
     * @param start the integer variable representing the start of {@code var}
     */
    public IntervalVarStart(CPIntervalVar var, CPIntVar start) {
        super(var.getSolver());
        this.var = var;
        this.start = start;
    }

    @Override
    public void post() {
        var.propagateOnChange(this);
        start.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        start.removeBelow(var.startMin());
        start.removeAbove(var.startMax());
        var.setStartMin(start.min());
        var.setStartMax(start.max());
    }
}