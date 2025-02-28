/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.scheduling.IntervalExpression;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

public interface IntervalVar extends Var, IntervalExpression {
    default boolean isFixed() {
        return isAbsent() || (
                startMin() == startMax() &&
                endMin() == endMax() &&
                lengthMin() == lengthMax()
        );
    }

    BoolVar status();

    default int slack(){
        return endMax() - startMin() - lengthMin();
    }

    @Override
    default Expression mapSubexpressions(Function<Expression, Expression> f){
        return this;
    }

    @Override
    default Collection<? extends Expression> computeSubexpressions(){
        return Collections.emptyList();
    }

    default boolean spanOver(int time) {
        if (isAbsent())
            return false;
        return startMin() <= time && time <= endMax();
    }
}
