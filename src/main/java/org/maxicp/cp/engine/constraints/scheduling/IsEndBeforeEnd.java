/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Reified is end before start constraint
 */
public class IsEndBeforeEnd extends AbstractCPConstraint {

    CPIntervalVar a;
    CPIntervalVar b;
    CPBoolVar isBefore;
    CPConstraint endABeforeEndB;
    CPConstraint endBPlusOneBeforeEndA;

    /**
     * Creates a constraint that link a boolean variable representing
     * whether an interval ends before the end of another one
     *
     * @param a        left hand side of the end before end operator
     * @param b        right hand side of the end before end operator
     * @param isBefore a boolean variable that is true if and only if
     *                 the end of a comes before or at the same time as the end of b
     * @see CPFactory#isEndBeforeEnd(CPIntervalVar, CPIntervalVar)
     */
    public IsEndBeforeEnd(CPIntervalVar a, CPIntervalVar b, CPBoolVar isBefore) {
        super(a.getSolver());
        this.a = a;
        this.b = b;
        this.isBefore = isBefore;
        endABeforeEndB = new EndBeforeEnd(a, b);
        endBPlusOneBeforeEndA = new EndBeforeEnd(CPFactory.delay(b, 1), a);
    }

    @Override
    public void post() {
        propagate();
        if (isActive()) {
            a.propagateOnChange(this);
            b.propagateOnChange(this);
            isBefore.propagateOnFix(this);
        }
    }

    @Override
    public void propagate() {
        if (isBefore.isTrue()) {  // end(a) <= end(b)
            getSolver().post(endABeforeEndB, false);
            setActive(false);
        } else if (isBefore.isFalse()) {
            // end(a) > end(b)  <->  end(b) < end(a)  <->  end(b+1) < end(a)
            getSolver().post(endBPlusOneBeforeEndA, false);
            setActive(false);
        } else {
            if (a.isPresent() && b.isPresent()) {
                if (a.endMax() <= b.endMin()) { // is end(a) <= end(b) ?
                    isBefore.fix(true);
                    setActive(false);
                } else if (b.endMax() < a.endMin()) {  // is end(a) > end(b) ?
                    isBefore.fix(false);
                    setActive(false);
                }
            }
            if (a.isAbsent() || b.isAbsent()) {
                setActive(false);
            }
        }
    }
}
