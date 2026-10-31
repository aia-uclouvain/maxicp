/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Constrains interval A to start before or at the end of interval B if both are present.
 *
 * @author Charles Thomas
 */
public class StartBeforeEnd extends AbstractCPConstraint {

    final CPIntervalVar A, B;

    /**
     * Creates a constraint enforcing {@code start(A) <= end(B)} whenever both {@code A} and {@code B} are present.
     *
     * @param A the first interval variable
     * @param B the second interval variable
     */
    public StartBeforeEnd(CPIntervalVar A, CPIntervalVar B) {
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
            B.setEndMin(A.startMin());
        }
        if (B.isPresent()) {
            A.setStartMax(B.endMax());
        }
        if (A.isAbsent() || B.isAbsent()) {
            setActive(false);
        }
    }
}
