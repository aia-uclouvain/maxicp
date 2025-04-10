/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.modeling.symbolic;

import org.maxicp.modeling.BoolVar;
import org.maxicp.modeling.IntervalVar;

public interface SymbolicIntervalVar extends SymbolicVar, IntervalVar {
    default int startMin() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).startMin();
        return defaultStartMin();
    }

    int defaultStartMin();

    default int startMax() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).startMax();
        return defaultStartMax();
    }

    int defaultStartMax();

    default int endMin() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).endMin();
        return defaultEndMin();
    }

    int defaultEndMin();

    default int endMax() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).endMax();
        return defaultEndMax();
    }

    int defaultEndMax();

    default int lengthMin() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).lengthMin();
        return defaultLengthMin();
    }

    int defaultLengthMin();

    default int lengthMax() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).lengthMax();
        return defaultLengthMax();
    }

    int defaultLengthMax();

    default boolean isPresent() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).isPresent();
        return defaultIsPresent();
    }

    boolean defaultIsPresent();

    default boolean isAbsent() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).isAbsent();
        return defaultIsAbsent();
    }

    boolean defaultIsAbsent();

    default boolean isOptional() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).isOptional();
        return defaultIsOptional();
    }

    boolean defaultIsOptional();

    default BoolVar status() {
        if (getModelProxy().isConcrete()) {
            return new IntervalStatus(this);
        }
        return defaultStatus();
    }

    BoolVar defaultStatus();

}
