/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.BoolVar;
import org.maxicp.modeling.ModelProxy;

public class IntervalVarImpl implements SymbolicIntervalVar {
    private final ModelProxy modelProxy;
    private final int startMin;
    private final int startMax;
    private final int endMin;
    private final int endMax;
    private final int lengthMin;
    private final int lengthMax;
    private final boolean isPresent;
    private final boolean isAbsent;

    public IntervalVarImpl(ModelProxy modelProxy, int startMin, int startMax, int endMin, int endMax, int lengthMin, int lengthMax, boolean isPresent) {
        this.modelProxy = modelProxy;
        this.startMin = startMin;
        this.startMax = startMax;
        this.endMin = endMin;
        this.endMax = endMax;
        this.lengthMin = lengthMin;
        this.lengthMax = lengthMax;
        this.isPresent = isPresent;
        this.isAbsent = false;
    }

    @Override
    public ModelProxy getModelProxy() {
        return modelProxy;
    }

    @Override
    public int defaultStartMin() {
        return startMin;
    }

    @Override
    public int defaultStartMax() {
        return startMax;
    }

    @Override
    public int defaultEndMin() {
        return endMin;
    }

    @Override
    public int defaultEndMax() {
        return endMax;
    }

    @Override
    public int defaultLengthMin() {
        return lengthMin;
    }

    @Override
    public int defaultLengthMax() {
        return lengthMax;
    }

    @Override
    public boolean defaultIsPresent() {
        return isPresent;
    }

    @Override
    public boolean defaultIsAbsent() {
        return isAbsent;
    }

    @Override
    public boolean defaultIsOptional() {
        return !isPresent && !isAbsent;
    }

    @Override
    public BoolVar defaultStatus() {
        return new IntervalStatus(this);
    }

    @Override
    public String toString() {
        if (getModelProxy().isConcrete()) {
            return getModelProxy().getConcreteModel().getConcreteVar(this).toString();
        }
        return show();
    }
}
