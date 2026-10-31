package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Constraint enforcing that a cumulative function {@code function} never exceeds {@code limit}
 * over its horizon.
 *
 * @param function the cumulative function
 * @param limit    the maximum allowed value for the cumulative function
 */
public record LessOrEqual(CumulFunction function, int limit) implements ConstraintFromRecord {

}
