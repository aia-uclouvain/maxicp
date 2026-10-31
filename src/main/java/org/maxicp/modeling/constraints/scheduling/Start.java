package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Constraint fixing the start time of an interval variable to a given constant value.
 *
 * @param intervalVar the interval variable
 * @param start       the fixed start time
 */
public record Start(IntervalVar intervalVar, int start) implements ConstraintFromRecord {

}