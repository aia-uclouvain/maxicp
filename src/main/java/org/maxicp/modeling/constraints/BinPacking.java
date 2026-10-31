/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.modeling.constraints;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.CacheScope;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

import java.util.Arrays;
import java.util.Collection;

/**
 * BinPacking constraint. {@code x[i]} is the bin assigned to item {@code i};
 * {@code loads[b]} is derived from the assignments and weights.
 * Only {@code x} is considered to contain decision variables.
 *
 * @param x       the bin assignment expression for each item
 * @param weights the weight of each item
 * @param loads   the load expression for each bin
 */
public record BinPacking(IntExpression[] x, int[] weights, IntExpression[] loads) implements ConstraintFromRecord, CacheScope, DecisionVarsProvider {

    @Override
    public Collection<IntExpression> decisionVariables() {
        // x[i] = bin assigned to item i (decision); loads are derived
        return Arrays.stream(x).filter(IntVar.class::isInstance).toList();
    }
}
