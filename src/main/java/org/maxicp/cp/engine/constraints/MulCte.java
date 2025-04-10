/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.cp.engine.constraints;


import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.util.NumberUtils;

import java.util.Arrays;

/**
 * Multiplication Constraint x * c = z where c is a constant
 * @author Pierre Schaus
 */
public class MulCte extends AbstractCPConstraint {

    private CPIntVar x, z;
    private int c;

    /**
     * x * c == z
     * @param x
     * @param c
     * @param z
     */
    public MulCte(CPIntVar x, int c, CPIntVar z) {
        super(x.getSolver());
        this.x = x;
        this.z = z;
        this.c = c;
    }

    @Override
    public void post() {
        propagate();
        if (isActive()) {
            if (!x.isFixed()) x.propagateOnBoundChange(this);
            if (!z.isFixed()) z.propagateOnBoundChange(this);
        }
		/*
		if (l == CPPropagStrength.Strong) {
			if (x.getSize() <= 100) { // remove all numbers not multiples of c if dom size to too big
				for (int v = z.getMin(); v <= z.getMax(); v++) {
					if (z.hasValue(v) && (v%c != 0)) {
						z.removeValue(v);
					}
				}
			}
		}*/
    }

    @Override
    public void propagate() {
        if (x.isFixed()) {
            z.fix(NumberUtils.safeMul(c , x.min()));
            setActive(false);
        }
        else if (c == 0) {
            z.fix(0);
            setActive(false);
        }
        else {
            z.removeBelow(Math.min(NumberUtils.safeMul(c , x.min()), NumberUtils.safeMul(c , x.max())));
            z.removeAbove(Math.max(NumberUtils.safeMul(c , x.min()), NumberUtils.safeMul(c , x.max())));
            x.removeBelow(Math.min(NumberUtils.ceilDiv(z.min(), c),  NumberUtils.ceilDiv(z.max(), c)));
            x.removeAbove(Math.max(NumberUtils.floorDiv(z.min(), c), NumberUtils.floorDiv(z.max(), c)));
        }
    }
}
