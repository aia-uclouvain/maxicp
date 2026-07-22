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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Sorted constraint: {@code y} is the sorted version of {@code x} with
 * position permutation {@code o} (i.e. {@code y[i] = x[o[i]]}).
 * {@code x} and {@code o} are the input decision variables; {@code y}
 * is the derived sorted output.
 */
public record Sorted(IntExpression[] x, IntExpression[] o, IntExpression[] y) implements ConstraintFromRecord, CacheScope, DecisionVarsProvider {

    @Override
    public Collection<IntExpression> decisionVariables() {
        // x (values to sort) and o (rank permutation) are decisions; y is derived
        List<IntExpression> result = new ArrayList<>();
        Arrays.stream(x).filter(IntVar.class::isInstance).forEach(result::add);
        return List.copyOf(result);
    }
}
