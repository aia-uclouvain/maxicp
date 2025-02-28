package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.VariableNotFixedException;

public class BoolVarImpl implements SymbolicBoolVar {
    private final boolean containsFalse;
    private final boolean containsTrue;
    private final ModelProxy modelProxy;

    public BoolVarImpl(ModelProxy modelProxy) {
        this(modelProxy, true, true);
    }

    public BoolVarImpl(ModelProxy modelProxy, boolean containsFalse, boolean containsTrue) {
        assert containsFalse || containsTrue;
        this.containsFalse = containsFalse;
        this.containsTrue = containsTrue;
        this.modelProxy = modelProxy;
    }

    @Override
    public int defaultMin() {
        return containsFalse ? 0 : 1;
    }

    @Override
    public int defaultMax() {
        return containsTrue ? 1 : 0;
    }

    @Override
    public int defaultSize() {
        return containsFalse && containsTrue ? 2 : 1;
    }

    @Override
    public boolean defaultContains(int v) {
        return (v == 0 && containsFalse) || (v == 1 && containsTrue);
    }

    @Override
    public boolean defaultEvaluateBool() throws VariableNotFixedException {
        if(containsFalse && containsTrue) throw new VariableNotFixedException();
        return containsTrue;
    }

    @Override
    public int defaultFillArray(int[] array) {
        if(containsFalse ^ containsTrue) {
            array[0] = containsFalse ? 0 : 1;
            return 1;
        }
        else { //must be both
            array[0] = 0;
            array[1] = 1;
            return 2;
        }
    }

    @Override
    public String toString() {
        if (getModelProxy().isConcrete()) {
            return getModelProxy().getConcreteModel().getConcreteVar(this).toString();
        }
        return defaultSize() == 1 ? String.format("%d", defaultMin()) : "{0, 1}";
    }

    @Override
    public ModelProxy getModelProxy() {
        return modelProxy;
    }
}
