package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

public record StartAfter(IntervalVar var, int start) implements ConstraintFromRecord {
}
