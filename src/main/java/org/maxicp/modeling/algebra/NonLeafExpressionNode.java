package org.maxicp.modeling.algebra;

import org.maxicp.modeling.ModelProxy;

public interface NonLeafExpressionNode extends Expression {
    /**
     * True if the variable is fixed
     */
    default boolean isFixed() {
        for(Expression t: subexpressions())
            if(!t.isFixed())
                return false;
        return true;
    }

    /**
     * Returns the ModelDispatcher linked to this Expression
     */
    default ModelProxy getModelProxy() {
        return subexpressions().iterator().next().getModelProxy();
    }
}
