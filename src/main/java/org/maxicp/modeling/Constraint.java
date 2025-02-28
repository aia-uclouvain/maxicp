/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling;

import org.maxicp.modeling.algebra.Expression;

import java.util.Collection;

public interface Constraint {
    /**
     * Returns the scope of the constraint, preferably as an immutable, unique collection.
     * @return the scope of the constraint, i.e. all the Expressions it uses. The collection should be immutable.
     */
    Collection<? extends Expression> scope();
}