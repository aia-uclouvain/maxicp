/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;

/**
 * Reified less or equal constraint.
 */
public class IsLessOrEqual extends AbstractCPConstraint { // b <=> x <= v

    private final CPBoolVar b;
    private final CPIntVar x;
    private final int v;

    /**
     * Creates a constraint that
     * link a boolean variable representing
     * whether one variable is less or equal to the given constant.
     * @param b a boolean variable that is true if and only if
     *         x takes a value less or equal to v
     * @param x the variable
     * @param v the constant
     * @see CPFactory#isLe(CPIntVar, int)
     */
    public IsLessOrEqual(CPBoolVar b, CPIntVar x, int v) {
        super(b.getSolver());
        this.b = b;
        this.x = x;
        this.v = v;
    }

    @Override
    public void post() {
        if (b.isTrue()) {
            x.removeAbove(v);
        } else if (b.isFalse()) {
            x.removeBelow(v + 1);
        } else if (x.max() <= v) {
            b.fix(1);
        } else if (x.min() > v) {
            b.fix(0);
        } else {
            b.whenFixed(() -> {
                // should deactivate the constraint as it is entailed
                if (b.isTrue()) {
                    x.removeAbove(v);

                } else {
                    x.removeBelow(v + 1);
                }
            });
            x.whenBoundChange(() -> {
                if (x.max() <= v) {
                    // should deactivate the constraint as it is entailed
                    b.fix(1);
                } else if (x.min() > v) {
                    // should deactivate the constraint as it is entailed
                    b.fix(0);
                }
            });
        }
    }
}
