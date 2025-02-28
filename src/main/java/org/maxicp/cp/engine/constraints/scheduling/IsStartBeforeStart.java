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
 * Reified is start before start constraint
 */
public class IsStartBeforeStart extends AbstractCPConstraint {

    CPIntervalVar a;
    CPIntervalVar b;
    CPBoolVar isBefore;
    CPConstraint startABeforeStartB;
    CPConstraint startBPlusOneBeforeStartA;

    /**
     * Creates a constraint that link a boolean variable representing
     * whether an interval start before the start of another one
     *
     * @param a        left hand side of the end before start operator
     * @param b        right hand side of the end before start operator
     * @param isBefore a boolean variable that is true if and only if
     *                 the start of a comes before or at the same time as the start of b
     * @see CPFactory#isStartBeforeStart(CPIntervalVar, CPIntervalVar)
     */
    public IsStartBeforeStart(CPIntervalVar a, CPIntervalVar b, CPBoolVar isBefore) {
        super(a.getSolver());
        this.a = a;
        this.b = b;
        this.isBefore = isBefore;
        startABeforeStartB = new StartBeforeStart(a, b);
        startBPlusOneBeforeStartA = new StartBeforeStart(CPFactory.delay(b, 1), a);
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
        if (isBefore.isTrue()) {  // start(a) <= start(b)
            getSolver().post(startABeforeStartB, false);
            setActive(false);
        } else if (isBefore.isFalse()) {
            // start(a) > start(b)  <->  start(b) < start(a)  <->  start(b+1) < start(a)
            getSolver().post(startBPlusOneBeforeStartA, false);
            setActive(false);
        } else {
            if (a.isPresent() && b.isPresent()) {
                if (a.startMax() <= b.startMin()) { // is start(a) <= start(b) ?
                    isBefore.fix(true);
                    setActive(false);
                } else if (b.startMax() < a.startMin()) {  // is start(a) > start(b) ?
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
