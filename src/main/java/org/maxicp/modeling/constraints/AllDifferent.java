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

public record AllDifferent(ImmutableSet<IntExpression> x) implements ConstraintFromRecord, CacheScope, DecisionVarsProvider {
    public AllDifferent(IntExpression... x) {
        this(ImmutableSet.of(x));
    }

    @Override
    public Collection<IntExpression> decisionVariables() {
        return x.stream().filter(IntVar.class::isInstance).toList();
    }
}
