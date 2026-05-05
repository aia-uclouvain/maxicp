package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Span constraint between intervals. If interval {@code span} is present, then it starts with the earliest interval
 * in {@code alternatives} and ends with the latest one
 * @param span interval that spans over the alternatives
 * @param alternatives intervals over which the span is computed
 */
public record Span(IntervalVar span, IntervalVar... alternatives) implements ConstraintFromRecord {
}
