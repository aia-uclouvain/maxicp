package org.maxicp.modeling.constraints;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.integer.Constant;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Disjunctive scheduling constraint ensuring that no two activities overlap in time.
 *
 * @param start    the start time expressions of the activities
 * @param duration the duration expressions of the activities
 */
public record Disjunctive(IntExpression[] start, IntExpression[] duration) implements ConstraintFromRecord, DecisionVarsProvider {

    /**
     * Creates a Disjunctive constraint with constant activity durations.
     *
     * @param start    the start time expressions of the activities
     * @param duration the constant durations of the activities
     */
    public Disjunctive(IntExpression[] start, int[] duration) {
        this(start, toConstants(start[0].getModelProxy(), duration));
    }

    private static IntExpression[] toConstants(ModelProxy mp, int[] values) {
        return Arrays.stream(values).mapToObj(v -> new Constant(mp, v)).toArray(IntExpression[]::new);
    }

    @Override
    public Collection<IntExpression> decisionVariables() {
        List<IntExpression> vars = new ArrayList<>();
        for (IntExpression s : start) if (s instanceof IntVar) vars.add(s);
        for (IntExpression d : duration) if (d instanceof IntVar) vars.add(d);
        return vars;
    }
}
