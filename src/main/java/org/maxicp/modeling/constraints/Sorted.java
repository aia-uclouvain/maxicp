/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.modeling.constraints;

import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.CacheScope;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

public record Sorted(IntExpression[] x, IntExpression[] o, IntExpression[] y) implements ConstraintFromRecord, CacheScope {
}
