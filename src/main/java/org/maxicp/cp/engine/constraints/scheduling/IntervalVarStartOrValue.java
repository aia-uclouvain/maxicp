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
 * @author Charles Thomas
 */
public class IntervalVarStartOrValue extends AbstractCPConstraint {
    CPIntervalVar var;
    CPIntVar start;
    int value;

    public IntervalVarStartOrValue(CPIntervalVar var, CPIntVar start, int value) {
        super(var.getSolver());
        this.var = var;
        this.start = start;
        this.value = value;
    }

    @Override
    public void post() {
        var.propagateOnChange(this);
        start.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        if (var.isPresent()) {
            start.removeBelow(var.startMin());
            start.removeAbove(var.startMax());
            var.setStartMin(start.min());
            var.setStartMax(start.max());
        } else if (var.isAbsent()) {
            start.fix(value);
            setActive(false);
        }
    }
}