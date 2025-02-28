/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;

/**
 * Checker for the generalized cumulative constraint.
 * It makes sure that we do not remove solutions, only used for testing purposes
 */
public class GeneralizedCumulativeChecker extends AbstractCPConstraint {
    private final Activity[] activities;
    protected final int maxCapacity;
    protected final int minCapacity;

    //Used by propagation:
    protected int[] profile;
    protected boolean[] covered;
    int offset; // offset to start indices at zero from the time

    public GeneralizedCumulativeChecker(Activity[] activities, int minCapacity, int maxCapacity) {
        super(activities[0].interval().getSolver());
        if (maxCapacity < 0) throw new IllegalArgumentException("The maximum capacity provided is < 0");
        if (minCapacity > maxCapacity)
            throw new IllegalArgumentException("The minimum capacity provided is > max capacity");

        this.activities = activities;
        this.maxCapacity = maxCapacity;
        this.minCapacity = minCapacity;

        int minTime = Integer.MAX_VALUE;
        int maxTime = Integer.MIN_VALUE;
        for (Activity act : activities) {
            minTime = Math.min(minTime, act.interval().startMin());
            maxTime = Math.max(maxTime, act.interval().endMax());
        }
        offset = minTime;
        profile = new int[maxTime - minTime + 1];
        covered = new boolean[maxTime - minTime + 1];
    }

    public GeneralizedCumulativeChecker(Activity[] activities, int maxCapacity) {
        this(activities, Integer.MIN_VALUE, maxCapacity);
    }

    @Override
    public void post() {
        for (Activity act : activities) {
            act.interval().propagateOnChange(this);
            act.height().propagateOnBoundChange(this);
        }
        propagate();
    }

    @Override
    public void propagate() {
        for (Activity act : activities) {
            if (!act.interval().isFixed() || (act.interval().isPresent() && !act.height().isFixed())) {
                return; // everything is fixed
            }
        }

        Arrays.fill(profile, 0);
        Arrays.fill(covered, false);
        for (Activity act : activities) {
            if (act.interval().isPresent()) {
                int start = act.interval().startMin() - offset;
                int end = act.interval().endMax() - offset;
                int height = act.height().min();
                for (int i = start; i < end; i++) {
                    profile[i] += height;
                    covered[i] = true;
                }
            }
        }

        for (int t = 0; t < covered.length; t++) {
            if (covered[t]) {
                if (profile[t] > maxCapacity || profile[t] < minCapacity) {
                    throw new InconsistencyException();
                }
            }
        }
    }
}