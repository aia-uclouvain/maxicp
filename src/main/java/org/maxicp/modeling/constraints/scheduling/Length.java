package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

public record Length(IntervalVar interval, int length) implements ConstraintFromRecord {

}
