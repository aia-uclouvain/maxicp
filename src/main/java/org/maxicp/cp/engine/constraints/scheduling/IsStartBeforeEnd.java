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
public class IsStartBeforeEnd extends AbstractCPConstraint {

    CPIntervalVar a;
    CPIntervalVar b;
    CPBoolVar isBefore;
    CPConstraint startABeforeEndB;
    CPConstraint endBPlusOneBeforeStartA;

    /**
     * Creates a constraint that link a boolean variable representing
     * whether an interval starts before the end of another one
     *
     * @param a        left hand side of the start before end operator
     * @param b        right hand side of the start before end operator
     * @param isBefore a boolean variable that is true if and only if
     *                 the start of a comes before or at the same time as the end of b
     * @see CPFactory#isStartBeforeEnd(CPIntervalVar, CPIntervalVar)
     */
    public IsStartBeforeEnd(CPIntervalVar a, CPIntervalVar b, CPBoolVar isBefore) {
        super(a.getSolver());
        this.a = a;
        this.b = b;
        this.isBefore = isBefore;
        startABeforeEndB = new StartBeforeEnd(a, b);
        endBPlusOneBeforeStartA = new EndBeforeStart(CPFactory.delay(b, 1), a);
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
        if (isBefore.isTrue()) {  // start(a) <= end(b)
            getSolver().post(startABeforeEndB, false);
            setActive(false);
        } else if (isBefore.isFalse()) {
            // start(a) > end(b)  <->  end(b) < start(a)  <->  end(b+1) < start(a)
            getSolver().post(endBPlusOneBeforeStartA, false);
            setActive(false);
        } else {
            if (a.isPresent() && b.isPresent()) {
                if (a.startMax() <= b.endMin()) { // is start(a) <= end(b) ?
                    isBefore.fix(true);
                    setActive(false);
                } else if (b.endMax() < a.startMin()) {  // is start(a) > end(b) ?
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
