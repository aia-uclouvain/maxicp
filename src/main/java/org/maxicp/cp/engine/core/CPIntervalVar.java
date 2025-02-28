/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.core;

import org.maxicp.modeling.concrete.ConcreteIntervalVar;

/**
 * TODO
 *
 * @author Pierre Schaus
 */
public interface CPIntervalVar extends CPVar, ConcreteIntervalVar {
    CPSolver getSolver();

    public boolean isFixed();

    void propagateOnChange(CPConstraint c);

    int startMin();

    int startMax();

    int endMin();

    int endMax();

    int lengthMin();

    int lengthMax();

    boolean isPresent();

    boolean isAbsent();

    boolean isOptional();

    /**
     * Return a variable linked to the status of the interval, that is present (true) or absent (false)
     * @return whether the interval is present (true) or absent (false)
     */
    CPBoolVar status();

    void setStartMin(int v);

    void setStartMax(int v);

    void setStart(int v);

    void setEndMin(int v);

    void setEndMax(int v);

    void setEnd(int v);

    void setLengthMin(int v);

    void setLengthMax(int v);

    void setLength(int v);

    void setPresent();

    void setAbsent();

    int slack();
}
