/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.state.datastructures.StateSparseSet;
import org.maxicp.util.exception.InconsistencyException;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * NoOverlap constraint, ensures that a set of interval variables do not overlap in time.
 * The filtering algorithm implemented are:
 * - Overload checking
 * - Detectable precedences
 * - Not-First, Not-Last
 * - Edge-finding
 *
 * @author Pierre Schaus, with the valuable contribution of Emma Legrand and Roger Kameugne for debugging
 */
public class NoOverlap extends AbstractCPConstraint {

    final CPIntervalVar[] vars;
    private CPBoolVar[] precedences;

    public NoOverlap(CPIntervalVar... vars) {
        super(vars[0].getSolver());
        this.vars = vars;
    }

    @Override
    public void post() {
        ArrayList<CPBoolVar> precedences = new ArrayList<>();
        for (int i = 0; i < vars.length; i++) {
            for (int j = i + 1; j < vars.length; j++) {
                NoOverlapBinary binary = new NoOverlapBinary(vars[i], vars[j]);
                getSolver().post(binary);
                precedences.add(binary.before);
            }
        }
        this.precedences = precedences.toArray(new CPBoolVar[0]);
        getSolver().post(new NoOverlapGlobal(vars));
    }

    /**
     * Return the precedence variables that are used to model the non-overlap constraint
     * They are n*(n-1)/2 variables where n is the number of interval variables.
     *
     * @return an array of boolean variables
     */
    public CPBoolVar[] precedenceVars() {
        return precedences;
    }
}


class NoOverlapGlobal extends AbstractCPConstraint {
    StateSparseSet activities;
    CPIntervalVar[] intervals;
    int[] iterator;
    boolean[] overlaps;
    int[] startMin;
    int[] endMax;
    int[] duration;
    boolean[] isOptional;
    long nCalls = 0;
    int n;

    NoOverlapLeftToRight globalFilter;

    NoOverlapGlobal(CPIntervalVar... vars) {
        super(vars[0].getSolver());
        this.intervals = vars;
        activities = new StateSparseSet(getSolver().getStateManager(), vars.length, 0);
        iterator = new int[vars.length];
        overlaps = new boolean[vars.length];
        startMin = new int[vars.length];
        endMax = new int[vars.length];
        duration = new int[vars.length];
        isOptional = new boolean[vars.length];
        globalFilter = new NoOverlapLeftToRight(vars.length);
    }

    private void update() {
        n = activities.fillArray(iterator);
        for (int iter = 0; iter < n; iter++) {
            CPIntervalVar act = intervals[iterator[iter]];
            if (act.isAbsent()) {
                activities.remove(iterator[iter]);
            }
        }
        filter();
        n = activities.fillArray(iterator);
        for (int iter = 0; iter < n; iter++) {
            int i = iterator[iter];
            CPIntervalVar act = intervals[i];
            startMin[iter] = act.startMin();
            endMax[iter] = act.endMax();
            duration[iter] = act.lengthMin();
            isOptional[iter] = !act.isPresent();
            assert (!act.isAbsent());
        }

    }

    private static boolean overlaps(CPIntervalVar a, CPIntervalVar b) {
        return a.startMin() < b.endMax() && b.startMin() < a.endMax();
    }

    private void filter() {
        nCalls++;
        if (nCalls % 100 != 0)  return;
        int n = activities.fillArray(iterator);
        if (n <= 1) {
            for (int i = 0; i < n; i++) {
                activities.remove(iterator[i]);
            }
            return;
        }

        // Sort by startMin
        sortByStartMin(iterator, 0, n - 1);

        Arrays.fill(overlaps,0, n, false);

        int activeStartIndex = 0;
        int currentMaxEnd = intervals[iterator[0]].endMax();

        for (int i = 1; i < n; i++) {

            int currIdx = iterator[i];
            int currStart = intervals[currIdx].startMin();
            int currEnd = intervals[currIdx].endMax();

            // If current starts before max end -> overlap
            if (currStart < currentMaxEnd) {

                overlaps[i] = true;
                overlaps[i - 1] = true;  // previous active interval overlaps too

                currentMaxEnd = Math.max(currentMaxEnd, currEnd);

            } else {
                currentMaxEnd = currEnd;
            }
        }

        // Remove those with no overlap
        for (int i = 0; i < n; i++) {
            if (!overlaps[i]) {
                activities.remove(iterator[i]);
            }
        }
    }

    private void sortByStartMin(int[] arr, int low, int high) {
        if (low >= high) return;

        int pivot = arr[(low + high) >>> 1];
        int pivotVal = intervals[pivot].startMin();

        int i = low, j = high;

        while (i <= j) {
            while (intervals[arr[i]].startMin() < pivotVal) i++;
            while (intervals[arr[j]].startMin() > pivotVal) j--;

            if (i <= j) {
                int tmp = arr[i];
                arr[i] = arr[j];
                arr[j] = tmp;
                i++;
                j--;
            }
        }

        sortByStartMin(arr, low, j);
        sortByStartMin(arr, i, high);
    }

    @Override
    public void post() {
        for (CPIntervalVar interval : intervals) {
            if (!interval.isAbsent()) {
                interval.propagateOnChange(this);
            }
        }
        propagate();
    }

    @Override
    public void propagate() {
        // left to right
        update();
        // set lct of optional to + infinity
        for (int i = 0; i < n; i++) {
            if (isOptional[i]) {
                endMax[i] = 1000000;
            }
        }
        NoOverlapLeftToRight.Outcome oc = globalFilter.filter(startMin, duration, endMax, n);
        if (oc == NoOverlapLeftToRight.Outcome.INCONSISTENCY) {
            throw InconsistencyException.INCONSISTENCY;
        } else if (oc == NoOverlapLeftToRight.Outcome.CHANGE) {
            // update startMin and endMax bounds
            for (int i = 0; i < n; i++) {
                CPIntervalVar interval = intervals[iterator[i]];
                if (isOptional[i]) {
                    if (startMin[i] > interval.endMax()) {
                        interval.setAbsent();
                        activities.remove(iterator[i]);
                    }
                } else {
                    intervals[iterator[i]].setStartMin(globalFilter.startMin[i]);
                    intervals[iterator[i]].setEndMax(globalFilter.endMax[i]);
                }
            }
        }

        // right to left
        update();
        // mirror the activities
        for (int i = 0; i < n; i++) {
            int startMinOld = startMin[i];
            startMin[i] = -endMax[i];
            endMax[i] = isOptional[i] ? 1000000 : -startMinOld;
        }
        oc = globalFilter.filter(startMin, duration, endMax, n);
        if (oc == NoOverlapLeftToRight.Outcome.INCONSISTENCY) {
            throw InconsistencyException.INCONSISTENCY;
        } else if (oc == NoOverlapLeftToRight.Outcome.CHANGE) {
            // update endMax variables
            for (int i = 0; i < n; i++) {
                CPIntervalVar interval = intervals[iterator[i]];
                if (isOptional[i]) {
                    if (-startMin[i] < interval.startMin()) {
                        interval.setAbsent();
                        activities.remove(iterator[i]);
                    }
                } else {
                    intervals[iterator[i]].setEndMax(-globalFilter.startMin[i]);
                    intervals[iterator[i]].setStartMin(-globalFilter.endMax[i]);
                }
            }
        }
    }
}