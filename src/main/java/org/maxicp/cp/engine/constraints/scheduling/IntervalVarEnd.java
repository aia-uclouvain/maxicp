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
public class IntervalVarEnd extends AbstractCPConstraint {
    CPIntervalVar var;
    CPIntVar end;

    public IntervalVarEnd(CPIntervalVar var, CPIntVar end) {
        super(var.getSolver());
        if (!var.isPresent()) {
            throw new IllegalArgumentException("interval var must be present:" + var);
        }
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