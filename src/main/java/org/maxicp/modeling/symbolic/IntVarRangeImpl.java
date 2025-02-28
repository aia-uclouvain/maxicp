/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.algebra.VariableNotFixedException;

public class IntVarRangeImpl implements SymbolicIntVar {

    private final int min;
    private final int max;
    private final ModelProxy modelProxy;
    private final String id;

    public IntVarRangeImpl(ModelProxy modelProxy, String id, int min, int max) {
        this.min = min;
        this.max = max;
        assert min <= max;
        this.id = id;
        this.modelProxy = modelProxy;
    }

    public IntVarRangeImpl(ModelProxy modelProxy, int min, int max) {
        this(modelProxy, null, min, max);
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        if(defaultMin() == defaultMax()) return defaultMin();
        throw new VariableNotFixedException();
    }

    @Override
    public int defaultMin() {
        return this.min;
    }

    @Override
    public int defaultMax() {
        return this.max;
    }

    @Override
    public int defaultSize() {
        return max-min+1;
    }

    @Override
    public boolean defaultContains(int v) {
        return min <= v && v <= max;
    }

    @Override
    public int defaultFillArray(int[] array) {
        for (int i = 0; i < defaultSize(); i++)
            array[i] = min + i;
        return defaultSize();
    }

    @Override
    public String toString() {
        if (getModelProxy().isConcrete()) {
            return getModelProxy().getConcreteModel().getConcreteVar(this).toString();
        }
        if(id != null)
            return id;
        return "{"+defaultMin()+".."+defaultMax()+"}";
    }

    @Override
    public ModelProxy getModelProxy() {
        return modelProxy;
    }
}
