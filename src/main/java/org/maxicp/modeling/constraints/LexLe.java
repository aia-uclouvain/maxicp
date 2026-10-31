package org.maxicp.modeling.constraints;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Lexicographic ordering constraint enforcing {@code x <=_lex y} (or {@code x <_lex y} if {@code strict} is true).
 *
 * @param x      the first array of integer expressions
 * @param y      the second array of integer expressions
 * @param strict {@code true} for strict lexicographic inequality ({@code <_lex}), {@code false} for non-strict ({@code <=_lex})
 */
public record LexLe(IntExpression[] x, IntExpression[] y, @IgnoreScope boolean strict) implements ConstraintFromRecord, DecisionVarsProvider {

    /**
     * Creates a non-strict lexicographic ordering constraint ({@code x <=_lex y}).
     *
     * @param x the first array of integer expressions
     * @param y the second array of integer expressions
     */
    public LexLe(IntExpression[] x, IntExpression[] y) {
        this(x, y, false);
    }

    @Override
    public Collection<IntExpression> decisionVariables() {
        List<IntExpression> vars = new ArrayList<>(x.length + y.length);
        for (IntExpression e : x) {
            if (e instanceof IntVar) vars.add(e);
        }
        for (IntExpression e : y) {
            if (e instanceof IntVar) vars.add(e);
        }
        return vars;
    }
}
