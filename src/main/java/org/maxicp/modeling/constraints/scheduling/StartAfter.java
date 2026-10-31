package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Constraint enforcing that an interval variable starts at or after a given time {@code start}.
 *
 * @param var   the interval variable
 * @param start the minimum start time
 */
public record StartAfter(IntervalVar var, int start) implements ConstraintFromRecord {
}
