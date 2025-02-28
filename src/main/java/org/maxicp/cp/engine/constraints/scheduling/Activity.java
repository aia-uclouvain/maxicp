/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * Represents a scheduling activity i.e. an interval variable that consumes an ammount of resource over time.
 *
 * @author Pierre Schaus, Charles Thomas
 */
public record Activity(CPIntervalVar interval, CPIntVar height) {

    public int getStartMin() {
        return interval.startMin();
    }

    public int getStartMax() {
        return interval.startMax();
    }

    public int getEndMin() {
        return interval.endMin();
    }

    public int getEndMax() {
        return interval.endMax();
    }

    public int getHeightMin() {
        return height.min();
    }

    public int getHeightMax() {
        return height.max();
    }

    public int getLengthMin() {
        return interval.lengthMin();
    }

    public int getLengthMax() {
        return interval.lengthMax();
    }

    public boolean isPresent() {
        return interval.isPresent();
    }

    public boolean isAbsent() {
        return interval.isAbsent();
    }

    public boolean isOptional() {
        return interval.isOptional();
    }

    public boolean hasFixedPartAt(int t) {
        return (getStartMax() <= t && getEndMin() > t);
    }

    public boolean hasFixedPart() {
        return getStartMax() < getEndMin();
    }

    public boolean isStartFixed() {
        return getStartMin() == getStartMax();
    }

    public boolean isLengthFixed() {
        return getLengthMin() == getLengthMax();
    }

    public boolean isEndFixed() {
        return getEndMin() == getEndMax();
    }

    public boolean isFixed() {
        return interval().isAbsent() || (interval().isFixed() && height().isFixed());
    }

    public void setStartMin(int val) {
        interval.setStartMin(val);
    }

    public void setStartMax(int val) {
        interval.setStartMax(val);
    }

    public void setEndMin(int val) {
        interval.setEndMin(val);
    }

    public void setEndMax(int val) {
        interval.setEndMax(val);
    }

    public void setLengthMin(int val) {
        interval.setLengthMin(val);
    }

    public void setLengthMax(int val) {
        interval.setLengthMax(val);
    }

    public void setHeightMin(int val) {
        if(val <= height.max()) height.removeBelow(val);
        else interval.setAbsent();
    }

    public void setHeightMax(int val) {
        if(val >= height.min()) height.removeAbove(val);
        else interval.setAbsent();
    }

    public void setPresent() {
        interval.setPresent();
    }

    public void setAbsent() {
        interval.setAbsent();
    }

    @Override
    public String toString() {
        return "Activity{" +
                "interval=" + interval +
                ", height=" + height +
                '}';
    }
}
