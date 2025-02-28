package org.maxicp.modeling.constraints;

import org.maxicp.modeling.algebra.bool.BoolExpression;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

public record ExpressionIsTrue(BoolExpression expr) implements ConstraintFromRecord {}
