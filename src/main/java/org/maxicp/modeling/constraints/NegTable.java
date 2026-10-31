package org.maxicp.modeling.constraints;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.CacheScope;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * Negative table constraint ensuring that the tuple of values assigned to {@code x} does not match
 * any forbidden tuple in {@code array}.
 *
 * @param x       the array of integer expressions
 * @param array   the table of forbidden tuples
 * @param starred optional wildcard value representing any value in a tuple
 */
public record NegTable(IntExpression[] x, int[][] array, Optional<Integer> starred) implements ConstraintFromRecord, CacheScope, DecisionVarsProvider {
    /**
     * Creates a negative table constraint without wildcard values.
     *
     * @param x     the array of integer expressions
     * @param array the table of forbidden tuples
     */
    public NegTable(IntExpression[] x, int[][] array) { this(x, array, Optional.empty()); }

    @Override
    public Collection<IntExpression> decisionVariables() {
        return Arrays.stream(x).filter(IntVar.class::isInstance).toList();
    }
}
