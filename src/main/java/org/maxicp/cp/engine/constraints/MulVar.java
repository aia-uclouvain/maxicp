/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.cp.engine.constraints;


import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.util.Arrays;
import org.maxicp.util.NumberUtils;
import org.maxicp.util.exception.InconsistencyException;

/**
 * Multiplication Constraint x * y = z (all variables)
 * @author Pierre Schaus
 */
public class MulVar extends AbstractCPConstraint {

    private CPIntVar x, y, z;

    /**
     * x * y == z
     * @param x
     * @param y
     * @param z
     */
    public MulVar(CPIntVar x, CPIntVar y, CPIntVar z) {
        super(x.getSolver());
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void post() {
        if (x == y) {
            getSolver().post(new Square(x,z));
        }
        else if (z.isFixed()) {
            getSolver().post(new MulCteRes(x,y,z.min()));
        } else {
            x.propagateOnBoundChange(this);
            y.propagateOnBoundChange(this);
            z.propagateOnBoundChange(this);
            propagate();
        }
    }

    @Override
    public void propagate() {
        if (!z.contains(0)) {
            x.remove(0);
            y.remove(0);
        }

        if (x.isFixed()) { // y * c = z
            getSolver().post(new MulCte(y,x.min(),z));
            setActive(false);
        }
        else if (y.isFixed()) { // x *c = z
            getSolver().post(new MulCte(x,y.min(),z));
            setActive(false);
        }
        else if (z.isFixed()) { // x * y = c
            getSolver().post(new MulCteRes(x,y,z.min()));
            setActive(false);
        }
        else { // none of the variables are bound

            assert (!x.isFixed() && !y.isFixed() && !z.isFixed());
            // propagation of z (try every combination of x and y's bounds)
            z.removeBelow(Arrays.min(NumberUtils.safeMul(x.min() , y.min()),
                    NumberUtils.safeMul(x.min() , y.max()),
                    NumberUtils.safeMul(x.max() , y.min()),
                    NumberUtils.safeMul(x.max() , y.max())));

            z.removeAbove(Arrays.max(NumberUtils.safeMul(x.min() , y.min()),
                    NumberUtils.safeMul(x.min() , y.max()),
                    NumberUtils.safeMul(x.max() , y.min()),
                    NumberUtils.safeMul(x.max() , y.max())));

            // propagate x
            propagateMul(x, y, z);
            // propagate y
            propagateMul(y, x, z);
        }
    }

    /**
     * Set min(w) <-- min( ceil(a/c), ceil(a/d), ceil(b/c), ceil(b/d))
     *     max(w) <-- max( floor(a/c), floor(a/d), floor(b/c), floor(b/d))
     * @param w
     * @param a
     * @param b
     * @param c != 0
     * @param d != 0
     * @return Suspend if no failure detected during this propagation
     */
    private void propagDiv(CPIntVar w, int a, int b, int c, int d) {
        int wmin = Math.min(NumberUtils.minCeilDiv(a, c, d), NumberUtils.minCeilDiv(b, c, d));
        w.removeBelow(wmin);
        int wmax = Math.max(NumberUtils.maxFloorDiv(a, c, d), NumberUtils.maxFloorDiv(b, c, d));
        w.removeAbove(wmax);
    }



    // propagate variable u for expression (u * w = z) with neither of the variable bound
    private void propagateMul(CPIntVar u, CPIntVar w, CPIntVar z) {
        if (w.min() > 0 || w.max() < 0) {
            propagDiv(u, z.min(), z.max(), w.min(), w.max());
            return;
        }
        // w_min < 0 && w_max > 0.
        else if (z.min() <= 0 && z.max() >= 0) {
            // cannot filter u because we potentially have u * 0 = 0
        }
        else {
            //it is possible for z to be fixed as before we call propagateMul there is a first cut on z domain.
            //assert(!z.isFixed());

            int after0 = w.after(0);
            int before0 = w.before(0);
            if (w.min() == 0) {
                propagDiv(u, z.min(), z.max(), after0, w.max());
            }
            else if (w.max() == 0) {
                propagDiv(u, z.min(), z.max(), w.min(), before0);
            }
            else {
                // w_min ... before0 ... 0 ... after0 ... w_max
                int umin = Math.min(NumberUtils.minCeilDiv(z.min(), w.min(), w.max(), before0, after0),
                        NumberUtils.minCeilDiv(z.max(), w.min(), w.max(), before0, after0));
                u.removeBelow(umin);
                int umax = Math.max(NumberUtils.maxFloorDiv(z.min(), w.min(), w.max(), before0, after0),
                        NumberUtils.maxFloorDiv(z.max(), w.min(), w.max(), before0, after0));
                u.removeAbove(umax);
            }
        }
    }
}
