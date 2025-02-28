package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;

public class StartAfter extends AbstractCPConstraint {

    final CPIntervalVar interval;
    final CPIntVar var;

    /**
     * Enforces that the start of an interval comes after or at a given variable
     * @param interval interval to constrain
     * @param var variable after which the interval must end
     */
    public StartAfter(CPIntervalVar interval, CPIntVar var) {
        super(interval.getSolver());
        this.interval = interval;
        this.var = var;
    }

    @Override
    public void post() {
        if (!interval.isAbsent()) {
            interval.propagateOnChange(this);
            var.propagateOnBoundChange(this);
            propagate();
        }
    }

    @Override
    public void propagate() {
        if (interval.isPresent()) {
            // only filters the variable if the interval is present
            var.removeAbove(interval.startMax());
        }
        interval.setStartMin(var.min());
        if (interval.isAbsent() || (interval.startMin() > var.max())) {
            setActive(false);
        }
    }
}