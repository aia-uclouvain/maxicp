package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Disjunctive scheduling constraint ensuring that no two present intervals in {@code intervals} overlap in time.
 *
 * @param intervals the interval variables that must not overlap
 */
public record NoOverlap(IntervalVar... intervals) implements ConstraintFromRecord {
}
