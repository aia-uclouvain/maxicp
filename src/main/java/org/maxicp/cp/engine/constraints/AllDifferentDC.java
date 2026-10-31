/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntVarImpl;
import org.maxicp.util.GraphUtil;
import org.maxicp.util.GraphUtil.Graph;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Arc Consistent AllDifferent Constraint
 * <p>
 * Algorithm described in
 * "A filtering algorithm for constraints of difference in CSPs" J-C. Régin, AAAI-94
 */
public class AllDifferentDC extends AtLeastNValueDC {

    /**
     * Creates an AllDifferent constraint that enforces that all variables in {@code x}
     * take pairwise different values, using domain-consistent filtering.
     *
     * @param x the array of variables that must take pairwise distinct values
     */
    public AllDifferentDC(CPIntVar... x) {
        super(x, new CPIntVarImpl(x[0].getSolver(), x.length, x.length));
    }

}
