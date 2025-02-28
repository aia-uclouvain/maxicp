package org.maxicp.modeling.algebra;

import org.maxicp.modeling.ModelProxy;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * An expression, that can be integer, boolean, or a sequence
 */
public interface Expression extends Serializable {
    /**
     * Should be implemented by all subclasses, but not used directly. Use subexpressions() instead, which is a cached version of this.
     *
     * Returns a collection that contains all sub-expressions of this expression.
     * It should not be modified. In practice, it should actually be unmutable or a copy.
     */
    Collection<? extends Expression> computeSubexpressions();

    /**
     * Returns a collection that contains all sub-expressions of this expression.
     * It should not be modified. In practice, it should actually be unmutable or a copy.
     *
     * Cached counterpart of computeSubexpressions().
     */
    default Collection<? extends Expression> subexpressions() {
        return SubexpressionsCache.cache.computeIfAbsent(this, Expression::computeSubexpressions);
    }



    /**
     * Apply a function on all sub-expressions of this expression and returns a new expression of the same type.
     * This function should return a value that is of the same class as the object that was given to it.
     */
    Expression mapSubexpressions(Function<Expression, Expression> f);

    /**
     * True if the expression is fixed
     */
    boolean isFixed();

    /**
     * Returns the ModelDispatcher linked to this Expression
     */
    ModelProxy getModelProxy();
}

class SubexpressionsCache {
    static Map<Expression, Collection<? extends Expression>> cache = Collections.synchronizedMap(new WeakHashMap<>());
}
