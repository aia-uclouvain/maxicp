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
 * Constraint linking the end time of an interval variable {@code var} to an integer variable {@code end}
 * when {@code var} is present, or fixing {@code end} to a default {@code value} when {@code var} is absent.
 *
 * @author Charles Thomas
 */
public class IntervalVarEndOrValue extends AbstractCPConstraint {
    CPIntervalVar var;
    CPIntVar end;
    int value;

    /**
     * Creates a constraint enforcing {@code end == end(var)} if {@code var} is present,
     * and {@code end == value} if {@code var} is absent.
     *
     * @param var   the interval variable
     * @param end   the integer variable representing the end of {@code var} or {@code value}
     * @param value the fallback value assigned to {@code end} when {@code var} is absent
     */
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