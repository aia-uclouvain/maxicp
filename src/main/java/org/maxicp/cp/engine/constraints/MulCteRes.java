/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;


import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntVarConstant;
import org.maxicp.util.NumberUtils;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Multiplication Constraint x * y = c
 * @author Pierre Schaus
 */
public class MulCteRes extends AbstractCPConstraint {

    private CPIntVar x, y;
    private int c;

    /**
     * x * y == c
     * @param x
     * @param y
     * @param c
     */
    public MulCteRes(CPIntVar x, CPIntVar y, int c) {
        super(x.getSolver());
        this.x = x;
        this.y = y;
        this.c = c;
    }

    @Override
    public void post() {

        if (x == y) {
            getSolver().post(new Square(x,new CPIntVarConstant(getSolver(),c)));
            setActive(false);
            return;
        }
        if (c == 0 && x.contains(0) && y.contains(0)) {
            x.propagateOnDomainChange(this);
            y.propagateOnDomainChange(this);
        } else {
            x.propagateOnBoundChange(this);
            y.propagateOnBoundChange(this);
        }
        // propagate must be called after attaching events because this propagator may not reach fix-point it-self.
        propagate();
    }

    @Override
    public void propagate() {

        if (c != 0) {
            x.remove(0);
            y.remove(0);
        }
        if (x.isFixed()) {
            getSolver().post(new MulCte(y,x.min(),new CPIntVarConstant(getSolver(),c)));
            setActive(false);
        } else if (y.isFixed()) {
            getSolver().post(new MulCte(x,y.min(),new CPIntVarConstant(getSolver(),c)));
            setActive(false);
        } else if (c == 0) {
            boolean xZero = x.contains(0);
            boolean yZero = y.contains(0);
            if (xZero || yZero) {
                if (xZero ^ yZero) {
                    if (xZero) {
                        x.fix(0);
                    } else {
                        y.fix(0);
                    }
                    setActive(false);
                }
            }
            else {
                throw InconsistencyException.INCONSISTENCY;
            }
        }
        else { // c != 0
            propagateVar(x,y);
            propagateVar(y,x);
        }
    }

    /**
     * Filter domain of z with w * z == c with c!=0
     */
    private void propagateVar(CPIntVar w , CPIntVar z) {
        int a = w.min();
        int b = w.max();

        assert (c != 0);
        //assert(a < b);

        if (a > 0 || b < 0) {
            // [a,b] > 0 or [a,b] < 0
            z.removeBelow(NumberUtils.minCeilDiv(c,a,b));
            z.removeAbove(NumberUtils.maxFloorDiv(c,a,b));
        } else if (a == 0) {
            int after0 = w.after(0);
            // a=0 ... after0 ... b
            z.removeBelow(NumberUtils.minCeilDiv(c,after0,b));
            z.removeAbove(NumberUtils.maxFloorDiv(c,after0,b));
        } else if (b == 0) {
            int before0 = w.before(0);
            // a ... before0 ... b=0
            z.removeBelow(NumberUtils.minCeilDiv(c,before0,a));
            z.removeAbove(NumberUtils.maxFloorDiv(c,before0,a));
        } else { // a ... 0 ... b
            int before0 = w.before(0);
            int after0 = w.after(0);
            z.removeBelow(NumberUtils.minCeilDiv(c, a, before0, after0, b));
            z.removeAbove(NumberUtils.maxFloorDiv(c, a, before0, after0, b));
        }
    }
}




