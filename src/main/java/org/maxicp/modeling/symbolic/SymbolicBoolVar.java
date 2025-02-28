/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.BoolVar;
import org.maxicp.modeling.algebra.bool.SymbolicBoolExpression;

public interface SymbolicBoolVar extends SymbolicIntVar, SymbolicBoolExpression, BoolVar {

}
