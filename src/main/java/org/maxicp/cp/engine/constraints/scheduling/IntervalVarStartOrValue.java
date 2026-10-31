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
 * Constraint linking the start time of an interval variable {@code var} to an integer variable {@code start}
 * when {@code var} is present, or fixing {@code start} to a default {@code value} when {@code var} is absent.
 *
 * @author Charles Thomas
 */
public class IntervalVarStartOrValue extends AbstractCPConstraint {
    CPIntervalVar var;
    CPIntVar start;
    int value;

    /**
     * Creates a constraint enforcing {@code start == start(var)} if {@code var} is present,
     * and {@code start == value} if {@code var} is absent.
     *
     * @param var   the interval variable
     * @param start the integer variable representing the start of {@code var} or {@code value}
     * @param value the fallback value assigned to {@code start} when {@code var} is absent
     */
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