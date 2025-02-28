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
public class IntervalVarEndOrValue extends AbstractCPConstraint {
    CPIntervalVar var;
    CPIntVar end;
    int value;

    public IntervalVarEndOrValue(CPIntervalVar var, CPIntVar end, int value) {
        super(var.getSolver());
        this.var = var;
        this.end = end;
        this.value = value;
    }

    @Override
    public void post() {
        var.propagateOnChange(this);
        end.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        if (var.isPresent()) {
            end.removeBelow(var.endMin());
            end.removeAbove(var.endMax());
            var.setEndMin(end.min());
            var.setEndMax(end.max());
        } else if (var.isAbsent()) {
            end.fix(value);
            setActive(false);
        }
    }
}