/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.*;

/**
 * Reified is end before start constraint
 */
public class IsEndBeforeStart extends AbstractCPConstraint {

    CPIntervalVar a;
    CPIntervalVar b;
    CPBoolVar isBefore;
    CPConstraint endABeforeStartB;
    CPConstraint startBPlusOneBeforeEndA;

    /**
     * Creates a constraint that link a boolean variable representing
     * whether an interval ends before the start of another one
     *
     * @param a        left hand side of the end before start operator
     * @param b        right hand side of the end before start operator
     * @param isBefore a boolean variable that is true if and only if
     *                 the end of a comes before or at the same time as the start of b
     * @see CPFactory#isEndBeforeStart(CPIntervalVar, CPIntervalVar)
     */
    public IsEndBeforeStart(CPIntervalVar a, CPIntervalVar b, CPBoolVar isBefore) {
        super(a.getSolver());
        this.a = a;
        this.b = b;
        this.isBefore = isBefore;
        endABeforeStartB = new EndBeforeStart(a, b);
        startBPlusOneBeforeEndA = new StartBeforeEnd(CPFactory.delay(b, 1), a);
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
        if (isBefore.isTrue()) {  // end(a) <= start(b)
            getSolver().post(endABeforeStartB, false);
            setActive(false);
        } else if (isBefore.isFalse()) {
            // end(a) > start(b)  <->  start(b) < end(a)  <->  start(b+1) < end(a)
            getSolver().post(startBPlusOneBeforeEndA, false);
            setActive(false);
        } else {
            if (a.isPresent() && b.isPresent()) {
                if (a.endMax() <= b.startMin()) { // is end(a) <= start(b) ?
                    isBefore.fix(true);
                    setActive(false);
                } else if (b.startMax() < a.endMin()) {  // is end(a) > start(b) ?
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
