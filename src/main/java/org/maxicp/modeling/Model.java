/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling;

import org.maxicp.modeling.symbolic.SymbolicModel;

public interface Model {
    SymbolicModel symbolicCopy();

    Iterable<Constraint> getConstraints();

    ModelProxy getModelProxy();
}
