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

public record CardinalityMax(IntExpression[] x, int[] array) implements ConstraintFromRecord, CacheScope, DecisionVarsProvider {

    @Override
    public Collection<IntExpression> decisionVariables() {
        return Arrays.stream(x).filter(IntVar.class::isInstance).toList();
    }
}
