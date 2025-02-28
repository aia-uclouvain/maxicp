/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import java.util.function.Consumer;

public class CPConstraintClosureWithDelta extends AbstractCPConstraint {


    private final Consumer<DeltaCPIntVar> filtering;
    private final CPIntVar x;
    DeltaCPIntVar delta;

    public CPConstraintClosureWithDelta(CPSolver cp, CPIntVar x, Consumer<DeltaCPIntVar> filtering) {
        super(cp);
        this.x = x;
        this.filtering = filtering;
    }

    @Override
    public void post() {
        delta = x.delta(this);
        x.propagateOnDomainChange(this);
    }

    @Override
    public void propagate() {
        filtering.accept(delta);
    }
}
