package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

public record AlwaysIn(CumulFunction expr, int minValue, int maxValue) implements ConstraintFromRecord {
}
