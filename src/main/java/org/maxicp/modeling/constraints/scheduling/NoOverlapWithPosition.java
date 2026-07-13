package org.maxicp.modeling.constraints.scheduling;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

/**
 * Modeling-level constraint for no-overlap with position variables.
 *
 * <p>Enforces that the intervals do not overlap and links them with position variables:
 * {@code posOfInterval[i]} is the position of interval i, and {@code intervalInPos[p]}
 * is the interval at position p. Optional minimum transition times can be specified.
 *
 * @param intervals        the interval variables
 * @param posOfInterval    position of each interval
 * @param intervalInPos    interval at each position
 * @param minTransition    n×n transition time matrix (null for zero transition times)
 */
public record NoOverlapWithPosition(
        IntervalVar[] intervals,
        IntExpression[] posOfInterval,
        IntExpression[] intervalInPos,
        int[][] minTransition
) implements ConstraintFromRecord {
}
