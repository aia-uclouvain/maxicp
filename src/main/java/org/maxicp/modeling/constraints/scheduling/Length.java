package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Constraint fixing the length of an interval variable to a given constant value.
 *
 * @param interval the interval variable
 * @param length   the required length of the interval when present
 */
public record Length(IntervalVar interval, int length) implements ConstraintFromRecord {

}
