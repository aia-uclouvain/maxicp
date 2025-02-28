/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling.constraints;

import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.CacheScope;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;
import org.maxicp.util.ImmutableSet;

public record AllDifferent(ImmutableSet<IntExpression> x) implements ConstraintFromRecord, CacheScope {
    public AllDifferent(IntExpression... x) {
        this(ImmutableSet.of(x));
    }
}
