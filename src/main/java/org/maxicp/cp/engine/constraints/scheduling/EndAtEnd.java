/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Enforces that interval A ends at interval B end if B is present
 * and that interval B ends at interval A end if A is present
 *
 * @author Charles Thomas
 */
public class EndAtEnd extends AbstractCPConstraint {
    final CPIntervalVar A, B;

    public EndAtEnd(CPIntervalVar A, CPIntervalVar B) {
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
            A.setEndMin(Math.max(B.endMin(), A.endMin()));
            A.setEndMax(Math.min(B.endMax(), A.endMax()));
        }
        if (A.isPresent()) {
            B.setEndMin(Math.max(A.endMin(), B.endMin()));
            B.setEndMax(Math.min(A.endMax(), B.endMax()));
        }
        if (A.isAbsent() || B.isAbsent()) {
            setActive(false);
        }
    }
}