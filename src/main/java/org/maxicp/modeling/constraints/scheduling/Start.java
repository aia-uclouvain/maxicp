package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

public record Start(IntervalVar intervalVar, int start) implements ConstraintFromRecord {

}