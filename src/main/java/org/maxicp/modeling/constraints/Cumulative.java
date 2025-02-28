package org.maxicp.modeling.constraints;

import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

public record Cumulative(IntExpression[] start, int[] duration, int[] demand, int capa) implements ConstraintFromRecord {

}
