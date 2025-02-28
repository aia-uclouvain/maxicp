/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.util.exception.InconsistencyException;

public class CPBoolVarIsEqual extends CPIntVarImpl implements CPBoolVar {

    public CPBoolVarIsEqual(CPIntVar x, int v) {
        super(x.getSolver(), 0, 1);

        if (!x.contains(v)) {
            fix(false);
        } else if (x.isFixed() && x.min() == v) {
            fix(true);
        } else {

            this.whenFixed(() -> {
                if (isTrue()) x.fix(v);
                else x.remove(v);
            });

            x.whenDomainChange(() -> {
                if (!x.contains(v)) {
                    this.fix(false);
                }
            });

            x.whenFixed(() -> {
                if (x.min() == v) {
                    fix(true);
                } else {
                    fix(false);
                }
            });

        }

    }

    @Override
    public boolean isTrue() {
        return min() == 1;
    }

    @Override
    public boolean isFalse() {
        return max() == 0;
    }

    @Override
    public void fix(boolean b) throws InconsistencyException {
        fix(b ? 1 : 0);
    }
}
