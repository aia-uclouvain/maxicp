/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntervalVar;

public class NoOverlapBinaryWithTransitionTime extends AbstractCPConstraint {
    final CPBoolVar before;
    final CPBoolVar after;
    final CPIntervalVar A, B;
    final int transitionTimeAB;
    final int transitionTimeBA;

    public NoOverlapBinaryWithTransitionTime(CPIntervalVar A, CPIntervalVar B, int transitionTimeAB, int transitionTimeBA) {
        super(A.getSolver());
        this.A = A;
        this.B = B;
        this.before = CPFactory.makeBoolVar(getSolver());
        this.after = CPFactory.not(before);
        this.transitionTimeAB = transitionTimeAB;
        this.transitionTimeBA = transitionTimeBA;
    }

    @Override
    public void post() {
        if (!A.isAbsent() && !B.isAbsent()) {
            A.propagateOnChange(this);
            B.propagateOnChange(this);
            before.propagateOnFix(this);
            propagate();
        }
    }

    @Override
    public void propagate() {
        if (A.isPresent() && B.isPresent()) {
            if (A.endMin() + transitionTimeAB > B.startMax() || before.isFalse()) {
                // B + transitionTimeBA << A
                before.fix(false);
                A.setStartMin(B.endMin() + transitionTimeBA);
                B.setEndMax(A.startMax() - transitionTimeBA);
            }
            if (B.endMin() > A.startMax() || before.isTrue()) {
                // A + transitionTimeAB << B
                before.fix(true);
                B.setStartMin(A.endMin() + transitionTimeAB);
                A.setEndMax(B.startMax() - transitionTimeAB);
            }
        }
        if (A.isAbsent() || B.isAbsent()) {
            setActive(false);
        }
    }
}
