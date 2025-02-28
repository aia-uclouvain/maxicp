package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.BoolVar;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.BoolVarImpl;

public interface BoolExpression extends IntExpression {
    boolean evaluateBool() throws VariableNotFixedException;

    default BoolVar reify() {
        BoolVar o = new BoolVarImpl(getModelProxy());
        getModelProxy().add(new Eq(o, this));
        return o;
    }
}
