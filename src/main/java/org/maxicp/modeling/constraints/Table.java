package org.maxicp.modeling.constraints;

import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.CacheScope;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

import java.util.Optional;

public record Table(IntExpression[] x, int[][] array, Optional<Integer> starred) implements ConstraintFromRecord, CacheScope {
    public Table(IntExpression[] x, int[][] array) { this(x, array, Optional.empty()); }
}
