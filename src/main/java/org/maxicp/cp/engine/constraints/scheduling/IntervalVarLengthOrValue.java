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
public class IntervalVarLengthOrValue extends AbstractCPConstraint {
    CPIntervalVar var;
    CPIntVar length;
    int value;

    public IntervalVarLengthOrValue(CPIntervalVar var, CPIntVar length, int value) {
        super(var.getSolver());
        this.var = var;
        this.length = length;
        this.value = value;
    }

    @Override
    public void post() {
        var.propagateOnChange(this);
        length.propagateOnBoundChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        //TODO: check and test this
        if (var.isPresent()) {
            length.removeBelow(var.lengthMin());
            length.removeAbove(var.lengthMax());
            var.setLengthMin(length.min());
            var.setLengthMax(length.max());
        } else if (var.isAbsent()) {
            length.fix(value);
            setActive(false);
        } else if(!length.contains(value)) {
            var.setPresent();
        } else if(length.min() != value){
            var.setLengthMin(length.min());
        } else if(length.max() != value){
            var.setLengthMax(length.max());
        }
    }
}