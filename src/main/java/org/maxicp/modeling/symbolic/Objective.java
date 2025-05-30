package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.VariableNotFixedException;

public interface Objective {
    ModelProxy getModelProxy();

    /**
     * Evaluate this objective. All variables referenced have to be fixed.
     * @throws VariableNotFixedException when a variable is not fixed
     * @return the value of this objective
     */
    int evaluate() throws VariableNotFixedException;
}

;