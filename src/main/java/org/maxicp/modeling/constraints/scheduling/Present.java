package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Constraint enforcing that an interval variable {@code intervalVar} must be present.
 *
 * @param intervalVar the interval variable that must be present
 */
public record Present(IntervalVar intervalVar) implements ConstraintFromRecord {
}
