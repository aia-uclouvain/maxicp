/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.integer.IntExpression;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

public interface IntVar extends Var, IntExpression {
    default boolean isFixed() {
        return size() == 1;
    }
    default IntVar mapSubexpressions(Function<Expression, Expression> f) {
        return this;
    }
    default Collection<Expression> computeSubexpressions() {
        return Collections.emptyList();
    }
}
