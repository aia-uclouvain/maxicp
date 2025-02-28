/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Enforces that interval A ends at interval B start if B is present
 * and that interval B starts at interval A end if A is present
 *
 * @author Charles Thomas
 */
public class EndAtStart extends AbstractCPConstraint {
    final CPIntervalVar A, B;

    public EndAtStart(CPIntervalVar A, CPIntervalVar B) {
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
        if (B.isPresent()) {
            A.setEndMin(Math.max(B.startMin(), A.endMin()));
            A.setEndMax(Math.min(B.startMax(), A.endMax()));
        }
        if (A.isPresent()) {
            B.setStartMin(Math.max(A.endMin(), B.startMin()));
            B.setStartMax(Math.min(A.endMax(), B.startMax()));
        }
        if (A.isAbsent() || B.isAbsent()) {
            setActive(false);
        }
    }
}