/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Constrains interval A to start before or at the start of interval B.
 *
 * @author Charles Thomas
 */
public class StartBeforeStart extends AbstractCPConstraint {

    final CPIntervalVar A, B;

    public StartBeforeStart(CPIntervalVar A, CPIntervalVar B) {
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
            B.setStartMin(A.startMin());
        }
        if (B.isPresent()) {
            A.setStartMax(B.startMax());
        }
        if (A.isAbsent() || B.isAbsent()) {
            setActive(false);
        }
    }
}
