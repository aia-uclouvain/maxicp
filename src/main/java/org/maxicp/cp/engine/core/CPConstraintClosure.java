/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

public class CPConstraintClosure extends AbstractCPConstraint {


    private final Runnable filtering;

    public CPConstraintClosure(CPSolver cp, Runnable filtering) {
        super(cp);
        this.filtering = filtering;
    }

    @Override
    public void post() {

    }

    @Override
    public void propagate() {
        filtering.run();
    }
}
