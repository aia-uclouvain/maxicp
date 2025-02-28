/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Constrains interval A to end before or at the start of interval B if both are present.
 *
 * @author Pierre Schaus
 */
public class EndBeforeStart extends AbstractCPConstraint {

    final CPIntervalVar A, B;

    public EndBeforeStart(CPIntervalVar A, CPIntervalVar B) {
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
            B.setStartMin(Math.max(A.endMin(), B.startMin()));
        }
        if (B.isPresent()) {
            A.setEndMax(Math.min(B.startMax(), A.endMax()));
        }
        if (A.isAbsent() || B.isAbsent()) {
            setActive(false);
        }
    }
}
