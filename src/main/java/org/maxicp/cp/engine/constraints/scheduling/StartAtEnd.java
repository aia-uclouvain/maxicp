/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Enforces that interval A starts at interval B end if B is present
 * and that interval B ends at interval A start if A is present
 *
 * @author Charles Thomas
 */
public class StartAtEnd extends AbstractCPConstraint {
    final CPIntervalVar A, B;

    public StartAtEnd(CPIntervalVar A, CPIntervalVar B) {
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
            A.setStartMin(Math.max(B.endMin(), A.startMin()));
            A.setStartMax(Math.min(B.endMax(), A.startMax()));
        }
        if (A.isPresent()) {
            B.setEndMin(Math.max(A.startMin(), B.endMin()));
            B.setEndMax(Math.min(A.startMax(), B.endMax()));
        }
        if (A.isAbsent() || B.isAbsent()) {
            setActive(false);
        }
    }
}
