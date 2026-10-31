package org.maxicp.modeling.constraints;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Among constraint ensuring that exactly {@code count} expressions in {@code x} take a value in {@code values}.
 *
 * @param x      the array of integer expressions
 * @param values the set of target values
 * @param count  the expression representing the number of expressions in {@code x} taking a value in {@code values}
 */
public record Among(IntExpression[] x, @IgnoreScope Set<Integer> values, IntExpression count) implements ConstraintFromRecord, DecisionVarsProvider {

    @Override
    public Collection<IntExpression> decisionVariables() {
        List<IntExpression> vars = new ArrayList<>(x.length + 1);
        for (IntExpression e : x) {
            if (e instanceof IntVar) vars.add(e);
        }
        if (count instanceof IntVar) vars.add(count);
        return vars;
    }
}
