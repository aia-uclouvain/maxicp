package org.maxicp.modeling.constraints;

import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

public record Circuit(IntExpression[] successor) implements ConstraintFromRecord {}