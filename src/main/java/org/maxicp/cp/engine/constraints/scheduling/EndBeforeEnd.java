/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Constrains interval A to end before or at the end of interval B.
 *
 * @author Charles Thomas
 */
public class EndBeforeEnd extends AbstractCPConstraint {

    final CPIntervalVar A, B;

    public EndBeforeEnd(CPIntervalVar A, CPIntervalVar B) {
        super(A.getSolver());
        this.A = A;
        this.B = B;
    }

    @Override
    public void post() {
        if (!A.isAbsent() && !B.isAbsent()) {
            A.propagateOnChange(this);
            B.propagateOnChange(this);
            propagate();
        }
    }

    @Override
    public void propagate() {
        if (A.isPresent()) {
            B.setEndMin(Math.max(A.endMin(), B.endMin()));
        }
        if (B.isPresent()) {
            A.setEndMax(Math.min(B.endMax(), A.endMax()));
        }
        if (A.isAbsent() || B.isAbsent()) {
            setActive(false);
        }
    }
}
