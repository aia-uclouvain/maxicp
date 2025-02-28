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

class NoOverlapBinary extends AbstractCPConstraint {
    final CPBoolVar before;
    final CPBoolVar after;
    final CPIntervalVar A, B;

    public NoOverlapBinary(CPIntervalVar A, CPIntervalVar B) {
        super(A.getSolver());
        this.A = A;
        this.B = B;
        this.before = CPFactory.makeBoolVar(getSolver());
        this.after = CPFactory.not(before);
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
            if (A.endMin() > B.startMax() || before.isFalse()) {
                // B << A
                before.fix(false);
                A.setStartMin(B.endMin());
                B.setEndMax(A.startMax());
            }
            if (B.endMin() > A.startMax() || before.isTrue()) {
                // A << B
                // A = [5..10] -> [10..15]
                // B = [12..13] -> [13..14]
                before.fix(true);
                B.setStartMin(A.endMin());
                A.setEndMax(B.startMax());
            }
        }
        if (A.isAbsent() || B.isAbsent()) {
            setActive(false);
        }
    }
}
