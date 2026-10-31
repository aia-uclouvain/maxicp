/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling.constraints;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.CacheScope;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;
import org.maxicp.util.ImmutableSet;

import java.util.Collection;

/**
 * AllDifferent constraint ensuring that all expressions in {@code x} take pairwise distinct values.
 *
 * @param x the set of integer expressions that must be pairwise distinct
 */
public record AllDifferent(ImmutableSet<IntExpression> x) implements ConstraintFromRecord, CacheScope, DecisionVarsProvider {
    /**
     * Creates an AllDifferent constraint over the given array of integer expressions.
     *
     * @param x the integer expressions that must be pairwise distinct
     */
    public AllDifferent(IntExpression... x) {
        this(ImmutableSet.of(x));
    }

    @Override
    public Collection<IntExpression> decisionVariables() {
        return x.stream().filter(IntVar.class::isInstance).toList();
    }
}
