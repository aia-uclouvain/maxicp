/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.core;

import org.maxicp.modeling.ModelProxy;

import static org.maxicp.Constants.HORIZON;

/**
 * Provides a view of a CPIntervalVar that is delayed by a fixed offset value.
 *
 */
public class CPIntervalVarOffset implements CPIntervalVar {

    private final CPIntervalVar interval;
    private final int offset;

    public CPIntervalVarOffset(CPIntervalVar intervalVar, int offset) {
        this.interval = intervalVar;
        this.offset = offset;
        setStartMin(0); // ensures that the interval does not span in negative values
        setEndMax(HORIZON); // ensures that the interval does not go above the horizon
    }

    @Override
    public CPSolver getSolver() {
        return interval.getSolver();
    }

    @Override
    public boolean isFixed() {
        return interval.isFixed();
    }

    @Override
    public void propagateOnChange(CPConstraint c) {
        interval.propagateOnChange(c);
    }

    @Override
    public int startMin() {
        return interval.startMin() + offset;
    }

    @Override
    public int startMax() {
        return interval.startMax() + offset;
    }

    @Override
    public int endMin() {
        return interval.endMin() + offset;
    }

    @Override
    public int endMax() {
        return interval.endMax() + offset;
    }

    @Override
    public int lengthMin() {
        return interval.lengthMin();
    }

    @Override
    public int lengthMax() {
        return interval.lengthMax();
    }

    @Override
    public boolean isPresent() {
        return interval.isPresent();
    }

    @Override
    public boolean isAbsent() {
        return interval.isAbsent();
    }

    @Override
    public boolean isOptional() {
        return interval.isOptional();
    }

    @Override
    public CPBoolVar status() {
        return interval.status();
    }

    @Override
    public void setStartMin(int v) {
        interval.setStartMin(v - offset);
    }

    @Override
    public void setStartMax(int v) {
        interval.setStartMax(v - offset);
    }

    @Override
    public void setStart(int v) {
        interval.setStart(v - offset);
    }

    @Override
    public void setEndMin(int v) {
        interval.setEndMin(v - offset);
    }

    @Override
    public void setEndMax(int v) {
        interval.setEndMax(v - offset);
    }

    @Override
    public void setEnd(int v) {
        interval.setEnd(v - offset);
    }

    @Override
    public void setLengthMin(int v) {
        interval.setLengthMin(v);
    }

    @Override
    public void setLengthMax(int v) {
        interval.setLengthMax(v);
    }

    @Override
    public void setLength(int v) {
        interval.setLength(v);
    }

    @Override
    public void setPresent() {
        interval.setPresent();
    }

    @Override
    public void setAbsent() {
        interval.setAbsent();
    }

    @Override
    public int slack() {
        return interval.slack();
    }

    @Override
    public ModelProxy getModelProxy() {
        return interval.getModelProxy();
    }

    @Override
    public String toString() {
        return show();
    }
}
