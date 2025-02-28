package org.maxicp.modeling.constraints.helpers;

import org.maxicp.modeling.Constraint;
import org.maxicp.modeling.algebra.Expression;

import java.util.Collection;
import java.util.WeakHashMap;

public interface CacheScope extends Constraint {
    /**
     * Computes the scope. Should be implemented by subclasses, but not used directly.
     * Use scope() instead, which is cached.
     *
     * @return the scope of the constraint, i.e. all the Expressions it uses. The collection should be immutable.
     */
    Collection<? extends Expression> computeScope();

    /**
     * Returns the scope of the constraint, preferably as an immutable, unique collection.
     * Cached conterpart of computeScope().
     *
     * @return the scope of the constraint, i.e. all the Expressions it uses. The collection should be immutable.
     */
    default Collection<? extends Expression> scope() {
        return ScopeCache.cache.computeIfAbsent(this, k -> this.computeScope());
    }
}

class ScopeCache {
    static WeakHashMap<CacheScope, Collection<? extends Expression>> cache = new WeakHashMap<>();
}