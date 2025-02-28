package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Exclusive alternative between intervals. If interval {@code real} is present, then exactly {@code n} intervals
 * in {@code alternatives} are present and starts and ends together with {@code real}
 * @param real interval to synchronize with {@code n} alternatives
 * @param n number of intervals in {@code alternatives} to synchronize
 * @param alternatives candidate for synchronization of interval {@code real}
 */
public record Alternative(IntervalVar real, IntExpression n, IntervalVar... alternatives) implements ConstraintFromRecord  {
}
