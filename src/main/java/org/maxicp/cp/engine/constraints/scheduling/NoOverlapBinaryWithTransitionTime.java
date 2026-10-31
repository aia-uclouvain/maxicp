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

/**
 * Binary NoOverlap constraint with transition times enforcing that two interval variables {@code A} and {@code B}
 * do not overlap and respect minimum setup/transition times {@code transitionTimeAB} (when {@code A} precedes {@code B})
 * and {@code transitionTimeBA} (when {@code B} precedes {@code A}) when both are present.
 */
public class NoOverlapBinaryWithTransitionTime extends AbstractCPConstraint {
    final CPBoolVar before;
    final CPBoolVar after;
    final CPIntervalVar A, B;
    final int transitionTimeAB;
    final int transitionTimeBA;

    /**
     * Creates a binary non-overlapping constraint with transition times between {@code A} and {@code B}.
     *
     * @param A                the first interval variable
     * @param B                the second interval variable
     * @param transitionTimeAB the minimum transition time when {@code A} precedes {@code B}
     * @param transitionTimeBA the minimum transition time when {@code B} precedes {@code A}
     */
    public NoOverlapBinaryWithTransitionTime(CPIntervalVar A, CPIntervalVar B, int transitionTimeAB, int transitionTimeBA) {
        super(A.getSolver());
        this.A = A;
        this.B = B;
        this.before = CPFactory.makeBoolVar(getSolver());
        this.after = CPFactory.not(before);
        this.transitionTimeAB = transitionTimeAB;
        this.transitionTimeBA = transitionTimeBA;
    }

    /**
     * Creates a binary non-overlapping constraint with transition times between {@code A} and {@code B},
     * channeled with a given precedence boolean variable {@code AprecedesB}.
     *
     * @param AprecedesB       a boolean variable that is true iff {@code A} precedes {@code B}
     * @param A                the first interval variable
     * @param B                the second interval variable
     * @param transitionTimeAB the minimum transition time when {@code A} precedes {@code B}
     * @param transitionTimeBA the minimum transition time when {@code B} precedes {@code A}
     */
    public NoOverlapBinaryWithTransitionTime(CPBoolVar AprecedesB, CPIntervalVar A, CPIntervalVar B, int transitionTimeAB, int transitionTimeBA) {
        super(A.getSolver());
        this.A = A;
        this.B = B;
        this.before = AprecedesB;
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
            if (B.endMin() + transitionTimeBA > A.startMax() || before.isTrue()) {
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
