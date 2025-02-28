package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;

public class EndBefore extends AbstractCPConstraint {

    final CPIntervalVar interval;
    final CPIntVar var;

    /**
     * Enforces that the end of an interval comes before or at a given variable
     * @param interval interval to constrain
     * @param var variable before which the interval must come
     */
    public EndBefore(CPIntervalVar interval, CPIntVar var) {
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
            var.removeBelow(interval.endMin());
        }
        interval.setEndMax(var.max());
        if (interval.isAbsent() || (interval.endMax() < var.min())) {
            setActive(false);
        }
    }
}
