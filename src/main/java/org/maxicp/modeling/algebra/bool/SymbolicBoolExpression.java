package org.maxicp.modeling.algebra.bool;

import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.SymbolicIntExpression;

public interface SymbolicBoolExpression extends BoolExpression, SymbolicIntExpression {
    @Override
    default int defaultMin() {
        if (isFixed()) {
            try {
                return evaluate();
            } catch (VariableNotFixedException ex) {
                throw new RuntimeException();
            }
        }
        return 0;
    }

    @Override
    default int defaultMax() {
        if (isFixed()) {
            try {
                return evaluate();
            } catch (VariableNotFixedException ex) {
                throw new RuntimeException();
            }
        }
        return 1;
    }

    @Override
    default int defaultFillArray(int[] array) {
        if (isFixed()) {
            try {
                array[0] = evaluate();
                return 1;
            } catch (VariableNotFixedException ex) {
                //never happens
                assert (false);
            }
        }
        array[0] = 0;
        array[1] = 1;
        return 2;
    }

    @Override
    default int defaultSize() {
        return isFixed() ? 1 : 2;
    }

    @Override
    default boolean defaultContains(int v) {
        return min() <= v && v <= max();
    }

    @Override
    default int defaultEvaluate() throws VariableNotFixedException {
        return evaluateBool() ? 1 : 0;
    }

    boolean defaultEvaluateBool() throws VariableNotFixedException;

    default boolean evaluateBool() throws VariableNotFixedException {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).evaluateBool();
        return defaultEvaluateBool();
    }
}
