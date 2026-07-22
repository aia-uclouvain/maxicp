package org.maxicp.modeling.constraints;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

import java.util.Arrays;
import java.util.Collection;

public record Disjunctive(IntExpression[] start, int[] duration) implements ConstraintFromRecord, DecisionVarsProvider {

    @Override
    public Collection<IntExpression> decisionVariables() {
        // start times are the decisions; durations are constants
        return Arrays.stream(start).filter(IntVar.class::isInstance).toList();
    }
}
