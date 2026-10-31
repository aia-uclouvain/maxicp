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
 * Constraint linking the end time of an interval variable {@code var} to an integer variable {@code end}.
 *
 * @author Pierre Schaus
 */
public class IntervalVarEnd extends AbstractCPConstraint {
    CPIntervalVar var;
    CPIntVar end;

    /**
     * Creates a constraint linking {@code end(var)} to {@code end}.
     *
     * @param var the interval variable
     * @param end the integer variable representing the end of {@code var}
     */
    public IntervalVarEnd(CPIntervalVar var, CPIntVar end) {
        super(var.getSolver());
        this.var = var;
        this.end = end;
    }

    @Override
    public void post() {
        var.propagateOnChange(this);
        end.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        end.removeBelow(var.endMin());
        end.removeAbove(var.endMax());
        var.setEndMin(end.min());
        var.setEndMax(end.max());
    }
}