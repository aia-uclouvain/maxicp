/*
 * mini-cp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License  v3
 * as published by the Free Software Foundation.
 *
 * mini-cp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY.
 * See the GNU Lesser General Public License  for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with mini-cp. If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
 *
 * Copyright (v)  2018. by Laurent Michel, Pierre Schaus, Pascal Van Hentenryck
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;


/**
 * Reified equality constraint
 * @see CPFactory#isEqual(CPIntVar, CPIntVar)
 */
public class IsEqualVar extends AbstractCPConstraint { // b <=> x == y

    private final CPBoolVar b;
    private final CPIntVar x;
    private final CPIntVar y;
    private final CPConstraint isEq;
    private final CPConstraint isNotEq;

    /**
     * Returns a boolean variable representing
     * whether two variables are equal
     * @param b the boolean variable that is set to true
     *          if and only if x == y
     * @param x first variable
     * @param y second variable
     */
    public IsEqualVar(CPBoolVar b, CPIntVar x, CPIntVar y) {
        super(b.getSolver());
        this.b = b;
        this.x = x;
        this.y = y;
        isEq = CPFactory.equal(x, y);
        isNotEq = CPFactory.notEqual(x, y);
    }

    @Override
    public void post() {
        propagate();
        if (isActive()) {
            x.propagateOnDomainChange(this);
            y.propagateOnDomainChange(this);
            b.propagateOnFix(this);
        }
    }

    @Override
    public void propagate() {
        if (b.isTrue()) {
            setActive(false); //needs to be first to avoid infinite loop
            this.getSolver().post(isEq);
        } else if (b.isFalse()) {
            setActive(false);
            this.getSolver().post(isNotEq);
        } else if (x.min() > y.max() || x.max() < y.min()) {
            setActive(false);
            b.fix(false);
        } else if (x.isFixed() && y.isFixed()) {
            setActive(false);
            b.fix(true); //always true due to the condition above
        }
    }
}
