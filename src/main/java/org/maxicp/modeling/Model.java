/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling;

import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.SymbolicModel;
import java.util.List;

public interface Model {

    SymbolicModel symbolicCopy();

    Iterable<Constraint> getConstraints();

    ModelProxy getModelProxy();

    default List<List<IntExpression>> getVariableGroups() {
        return List.of();
    }
}
