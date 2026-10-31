package org.maxicp.modeling.constraints;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

import java.util.Arrays;
import java.util.Collection;

/**
 * Hamiltonian circuit constraint ensuring that the successor array forms a single Hamiltonian circuit
 * visiting all nodes {@code 0..successor.length - 1}.
 *
 * @param successor the array of successor expressions for each node
 */
public record Circuit(IntExpression[] successor) implements ConstraintFromRecord, DecisionVarsProvider {

    @Override
    public Collection<IntExpression> decisionVariables() {
        return Arrays.stream(successor).filter(IntVar.class::isInstance).toList();
    }
}
