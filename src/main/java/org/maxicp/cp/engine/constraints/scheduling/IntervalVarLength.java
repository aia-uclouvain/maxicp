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
public class IntervalVarLength extends AbstractCPConstraint {
    CPIntervalVar var;
    CPIntVar length;

    public IntervalVarLength(CPIntervalVar var, CPIntVar length) {
        super(var.getSolver());
        if (!var.isPresent()) {
            throw new IllegalArgumentException("interval var must be present:" + var);
        }
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