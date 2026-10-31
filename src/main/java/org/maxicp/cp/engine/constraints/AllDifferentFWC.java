/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.cp.engine.constraints;


import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;

/**
 * Forward Checking AllDifferent Constraint.
 * <p>
 * Enforces that all variables in {@code x} take pairwise different values
 * by removing the value of a variable from all other variables' domains as soon as it becomes fixed.
 */
public class AllDifferentFWC extends AtLeastNValueFWC {

    /**
     * Creates an AllDifferent constraint with forward checking filtering.
     *
     * @param x the array of variables that must take pairwise distinct values
     */
    public AllDifferentFWC(CPIntVar... x) {
        super(x, CPFactory.makeIntVar(x[0].getSolver(), x.length, x.length));
    }

}