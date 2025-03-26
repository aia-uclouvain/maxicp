/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;

/**
 * Square Constraint x * x = y
 * @author Pierre Schaus
 */
public class Square extends AbstractCPConstraint {

    private CPIntVar x;
    private CPIntVar y;

    /**
     * x*x == y
     * @param x
     * @param y
     */
    public Square(CPIntVar x, CPIntVar y) {
        super(x.getSolver());
        this.x = x;
        this.y = y;
    }

    @Override
    public void post() {
        y.removeBelow(0);
        propagate();
        if (isActive()) {
            if (!x.isFixed()) {
                x.propagateOnDomainChange(this);
            }
            if (!y.isFixed()) {
                y.propagateOnBoundChange(this);
            }
        }
    }

    @Override
    public void propagate() {
        // propagation of y
        int mx = x.min();
        int Mx = x.max();
        int mx2 = mx * mx;
        int Mx2 = Mx * Mx;

        // propagate y (which is not bound)
        if (mx >= 0) { // x will be positive
            y.removeBelow(mx2);
            y.removeAbove(Mx2);
        } else if (Mx <= 0) { // x is non-positive
            y.removeBelow(Mx2);
            y.removeAbove(mx2);
        } else if (x.contains(0)) {
            // y min is already >= 0 (post does it)
            y.removeAbove(Math.max(mx2, Mx2));
        } else {
            int a = x.before(0);
            int b = x.after(0);
            int a2 = a * a;
            int b2 = b * b;
            y.removeBelow(Math.min(a2, b2));
            y.removeAbove(Math.max(a2, b2));
        }
        //propagate x (which is not bound)
        int my = y.min();
        int My = y.max();

        int rootm = (int) (Mx <= 0 ? Math.ceil(Math.sqrt(my)) : Math.sqrt(my));
        int rootM = (int) Math.sqrt(My);

        if (mx >= 0) {
            x.removeBelow(rootm);
            x.removeAbove(rootM);
        } else if (Mx <= 0) {
            x.removeAbove(-rootm);
            x.removeBelow(-rootM);
        } else {
            x.removeBelow(-rootM);
            x.removeAbove(rootM);
			/*
			for (int v = -rootm+1; v < rootm; v++) {
				x.removeValue(v);
			}*/
        }
    }

}
