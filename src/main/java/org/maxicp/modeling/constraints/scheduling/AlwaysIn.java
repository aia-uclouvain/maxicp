package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Constraint that enforces a {@link CumulFunction} to remain within bounds
 * at every point overlapping the execution of at least one interval.
 *
 * <p>Specifically, for all time points {@code t} overlapping at least one interval:
 * <pre>
 *   minValue <= expr(t) <= maxValue
 * </pre>
 *
 * <p>This is typically used in cumulative scheduling to enforce resource capacity limits,
 * e.g. a resource that can handle at most {@code maxValue} concurrent tasks and must
 * never drop below {@code minValue} (e.g. for inventory or stock constraints).
 *
 * @param expr     the cumulative function to constrain
 * @param minValue the minimum allowed value of the function at any time point
 * @param maxValue the maximum allowed value of the function at any time point
 */
public record AlwaysIn(CumulFunction expr, int minValue, int maxValue) implements ConstraintFromRecord {
}
