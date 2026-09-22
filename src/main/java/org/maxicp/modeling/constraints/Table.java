package org.maxicp.modeling.constraints;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.CacheScope;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public record Table(IntExpression[] x, int[][] array, Optional<Integer> starred) implements ConstraintFromRecord, CacheScope, DecisionVarsProvider {
    public Table(IntExpression[] x, int[][] array) { this(x, array, Optional.empty()); }

    @Override
    public Collection<IntExpression> decisionVariables() {
        return Arrays.stream(x).filter(IntVar.class::isInstance).toList();
    }
}
