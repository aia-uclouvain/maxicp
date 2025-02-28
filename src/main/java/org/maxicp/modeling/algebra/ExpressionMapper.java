package org.maxicp.modeling.algebra;

import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.sequence.SeqExpression;

import java.util.function.Function;

public class ExpressionMapper {
    public static Function<Expression, Expression> recursiveIntExprMapper(Function<IntExpression, IntExpression> f) {
        Function<Expression, Expression> f2 = (x) -> {
            switch (x) {
                case IntExpression ie -> { return f.apply(ie); }
                default -> { return x; }
            }
        };
        return (x) -> recurHelper(x, f2);
    }

    public static Function<Expression, Expression> recursiveSequenceExprMapper(Function<SeqExpression, SeqExpression> f) {
        Function<Expression, Expression> f2 = (x) -> {
            switch (x) {
                case SeqExpression ie -> { return f.apply(ie); }
                default -> { return x; }
            }
        };
        return (x) -> recurHelper(x, f2);
    }

    public static Function<Expression, Expression> recursiveExpressionMapper(Function<Expression, Expression> f) {
        return (x) -> recurHelper(x, f);
    }

    private static Expression recurHelper(Expression expr, Function<Expression, Expression> f) {
        return f.apply(expr).mapSubexpressions((x) -> recurHelper(x, f));
    }
}
