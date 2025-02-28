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
 * TODO
 *
 * @author Pierre Schaus
 */
public class IntervalVarStart extends AbstractCPConstraint {
    CPIntervalVar var;
    CPIntVar start;

    public IntervalVarStart(CPIntervalVar var, CPIntVar start) {
        super(var.getSolver());
        if (!var.isPresent()) {
            throw new IllegalArgumentException("interval var must be present:" + var);
        }
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