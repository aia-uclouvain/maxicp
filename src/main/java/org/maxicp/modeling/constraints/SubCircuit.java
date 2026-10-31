package org.maxicp.modeling.constraints;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

import java.util.Arrays;
import java.util.Collection;

/**
 * SubCircuit constraint ensuring that the successor array forms at most one circuit
 * while nodes with {@code successor[i] == i} are self-loops not part of the circuit.
 *
 * @param successor the array of successor expressions for each node
 */
public record SubCircuit(IntExpression[] successor) implements ConstraintFromRecord, DecisionVarsProvider {

    @Override
    public Collection<IntExpression> decisionVariables() {
        return Arrays.stream(successor).filter(IntVar.class::isInstance).toList();
    }
}

