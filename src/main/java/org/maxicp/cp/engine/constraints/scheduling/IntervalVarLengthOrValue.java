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
 * Constraint linking the length (duration) of an interval variable {@code var} to an integer variable {@code length}
 * when {@code var} is present, or fixing {@code length} to a default {@code value} when {@code var} is absent.
 *
 * @author Charles Thomas
 */
public class IntervalVarLengthOrValue extends AbstractCPConstraint {
    CPIntervalVar var;
    CPIntVar length;
    int value;

    /**
     * Creates a constraint enforcing {@code length == length(var)} if {@code var} is present,
     * and {@code length == value} if {@code var} is absent.
     *
     * @param var    the interval variable
     * @param length the integer variable representing the length of {@code var} or {@code value}
     * @param value  the fallback value assigned to {@code length} when {@code var} is absent
     */
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