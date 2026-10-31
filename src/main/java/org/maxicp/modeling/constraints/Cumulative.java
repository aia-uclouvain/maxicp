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
 * Cumulative scheduling constraint ensuring that at any point in time, the total resource demand
 * of overlapping activities does not exceed the resource capacity {@code capa}.
 *
 * @param start    the start time expressions of the activities
 * @param duration the duration expressions of the activities
 * @param end      the end time expressions of the activities (or empty if not explicitly provided)
 * @param demand   the resource demand expressions of the activities
 * @param capa     the resource capacity expression
 */
public record Cumulative(
        IntExpression[] start,
        IntExpression[] duration,
        IntExpression[] end,
        IntExpression[] demand,
        IntExpression capa) implements ConstraintFromRecord, DecisionVarsProvider {

    public Cumulative {
        if (end == null) {
            end = new IntExpression[0];
        }
    }

    /**
     * Creates a Cumulative constraint with constant durations, demands, and capacity.
     *
     * @param start    the start time expressions of the activities
     * @param duration the durations of the activities
     * @param demand   the resource demands of the activities
     * @param capa     the resource capacity
     */
    public Cumulative(IntExpression[] start, int[] duration, int[] demand, int capa) {
        this(
                start,
                toConstants(start[0].getModelProxy(), duration),
                new IntExpression[0],
                toConstants(start[0].getModelProxy(), demand),
                new Constant(start[0].getModelProxy(), capa));
    }

    private static IntExpression[] toConstants(ModelProxy mp, int[] values) {
        return Arrays.stream(values).mapToObj(v -> new Constant(mp, v)).toArray(IntExpression[]::new);
    }

    @Override
    public Collection<IntExpression> decisionVariables() {
        List<IntExpression> vars = new ArrayList<>();
        for (IntExpression s : start) if (s instanceof IntVar) vars.add(s);
        for (IntExpression d : duration) if (d instanceof IntVar) vars.add(d);
        if (end != null) {
            for (IntExpression e : end) if (e instanceof IntVar) vars.add(e);
        }
        for (IntExpression h : demand) if (h instanceof IntVar) vars.add(h);
        if (capa instanceof IntVar) vars.add(capa);
        return vars;
    }
}
