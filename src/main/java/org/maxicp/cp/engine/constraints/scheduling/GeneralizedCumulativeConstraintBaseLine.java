/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.Constants;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;
import org.maxicp.state.datastructures.StateSparseSet;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.Comparator;

import static org.maxicp.Constants.HORIZON;

/**
 * Generalized Cumulative Constraint using Timetabling
 * TODO Refer paper once published
 *
 * @author Roger Kameugne, Charles Thomas, Pierre Schaus
 */
public class GeneralizedCumulativeConstraintBaseLine extends AbstractCPConstraint {
    private final Activity[] activities;
    protected final long maxCapacity;
    protected final long minCapacity;

    //Propagation structures:
    protected final StateSparseSet activeSet; //Tracks active activities
    protected final int[] active; //Indices of the active activities
    protected final StateInt minStart; //Minimum start of all non-fixed activities
    protected final StateInt maxEnd; //Maximum end of all non-fixed activities

    //Structures used for timeline initialization:
    protected final Event[] events; //Events used to build profile
    public final int[] actToStartMinTp; //Pointers to start min time points
    public final int[] actToEndMaxTp; //Pointers to end max time points

    // Profile structure implemented as arrays:
    protected final int[] time; //Time of time points
    protected final int[] profileMin; //Minimum Profile
    protected final int[] profileMax; //Maximum Profile
    protected final int[] nOverlap; //Number of overlapping tasks
    protected int lastTP; //Index of last time point

    //Structures used to remember profile at start of propagation:
    protected final boolean[] isPresentInProfile; // Tracks which tasks are present at start of propagation
    protected final int[] startMaxInProfile; // Tracks start max of tasks at start of propagation
    protected final int[] endMinInProfile; // Tracks end min of tasks at start of propagation

    // Boolean flags for propagation rule activation:
    protected boolean allHeightFixed;
    protected boolean allLengthFixed;
    protected boolean allPositive;
    protected boolean allNegative;
    protected boolean hasMinCapa;
    protected boolean mandatoryActive;
    protected boolean simpleCumulative;

    /**
     * Creates an new Generalized Cumulative Constraint
     *
     * @param activities  array of activities
     * @param minCapacity minimum capacity
     * @param maxCapacity maximum capacity
     */
    public GeneralizedCumulativeConstraintBaseLine(Activity[] activities, int minCapacity, int maxCapacity) {
        super(activities[0].interval().getSolver());
        if (minCapacity > maxCapacity)
            throw new IllegalArgumentException("The minimum capacity provided is > max capacity");
        this.activities = activities.clone();
        this.maxCapacity = maxCapacity;
        this.minCapacity = minCapacity;
        hasMinCapa = minCapacity > Integer.MIN_VALUE;

        StateManager sm = activities[0].interval().getSolver().getStateManager();
        activeSet = new StateSparseSet(sm, nMax(), 0);
        active = new int[nMax()];
        minStart = sm.makeStateInt(0);
        maxEnd = sm.makeStateInt(HORIZON);

        events = new Event[nMax() * 4];
        actToStartMinTp = new int[nMax()];
        actToEndMaxTp = new int[nMax()];

        time = new int[nMax() * 4 + 1];
        profileMin = new int[nMax() * 4 + 1];
        profileMax = new int[nMax() * 4 + 1];
        nOverlap = new int[nMax() * 4 + 1];

        isPresentInProfile = new boolean[nMax()];
        startMaxInProfile = new int[nMax()];
        endMinInProfile = new int[nMax()];
    }

    /**
     * Creates an new Generalized Cumulative Constraint with only a maximum capacity
     *
     * @param activities  array of activities
     * @param maxCapacity maximum capacity
     */
    public GeneralizedCumulativeConstraintBaseLine(Activity[] activities, int maxCapacity) {
        this(activities, Integer.MIN_VALUE, maxCapacity);
    }

    public int nMax() {
        return activities.length;
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
    public int priority() {
        return Constants.PIORITY_MEDIUM;
    }

    @Override
    public void propagate() {
        //Checking active activities:
        int nActive = activeSet.fillArray(active);
        int minS = Integer.MAX_VALUE;
        int maxE = Integer.MIN_VALUE;
        int j = 0;
        while (j < nActive) {
            int a = active[j];
            if (activities[a].isAbsent()) {
                activeSet.remove(a);
                active[j] = active[nActive - 1];
                nActive--;
            } else {
                if (!activities[a].isFixed()) {
                    minS = Math.min(minS, activities[a].getStartMin());
                    maxE = Math.max(maxE, activities[a].getEndMax());
                }
                j++;
            }
        }

        // Preparing data:
        allHeightFixed = true;
        allLengthFixed = true;
        allPositive = true;
        allNegative = true;
        for (int i = 0; i < nActive; i++) {
            int a = active[i];
            if (activeSet.contains(a)) {
                if (!activities[a].height().isFixed()) allHeightFixed = false;
                if (!activities[a].isLengthFixed()) allHeightFixed = false;
                if (activities[a].getHeightMin() < 0) allPositive = false;
                if (activities[a].getHeightMax() > 0) allNegative = false;
                isPresentInProfile[a] = activities[a].isPresent();
                startMaxInProfile[a] = activities[a].getStartMax();
                endMinInProfile[a] = activities[a].getEndMin();
            }
        }
        mandatoryActive = (!allPositive && !allNegative) || hasMinCapa;
        simpleCumulative = !mandatoryActive && allHeightFixed && allLengthFixed;

        //Propagation:
        if (nActive > 0 && (minCapacity > Integer.MIN_VALUE || maxCapacity < Integer.MAX_VALUE)) {
            initializeTimeline(nActive);
            timeTabling(nActive);
        }

        //Removing inactive activities:
        minStart.setValue(minS);
        maxEnd.setValue(maxE);
        for (int i = 0; i < nActive; i++) {
            int a = active[i];
            if (activeSet.contains(a) && (activities[a].getEndMax() <= minS || activities[a].getStartMin() >= maxE))
                activeSet.remove(a);
        }
    }

    //Creates timeline structure:
    public void initializeTimeline(int n) {
        //Resetting events:
        int nEvents = 0;
        for (int i = 0; i < n; i++) {
            if (events[nEvents] == null) events[nEvents] = new Event(active[i], eventType.START_MIN);
            else events[nEvents].reset(active[i], eventType.START_MIN);
            if (events[nEvents + 1] == null) events[nEvents + 1] = new Event(active[i], eventType.END_MAX);
            else events[nEvents + 1].reset(active[i], eventType.END_MAX);
            nEvents += 2;
            if (activities[active[i]].isPresent() && activities[active[i]].hasFixedPart()) {
                if (events[nEvents] == null) events[nEvents] = new Event(active[i], eventType.START_MAX);
                else events[nEvents].reset(active[i], eventType.START_MAX);
                if (events[nEvents + 1] == null) events[nEvents + 1] = new Event(active[i], eventType.END_MIN);
                else events[nEvents + 1].reset(active[i], eventType.END_MIN);
                nEvents += 2;
            }
        }
        Arrays.sort(events, 0, nEvents, Comparator.comparing(Event::getTime)); //Sorting events by time

        lastTP = 0;
        resetTP(lastTP, minStart.value(), 0, 0, 0);

        //Iterating over events to create time points:
        for (int i = 0; i < nEvents; i++) {
            Event event = events[i];
            int t = event.getTime();
            Activity act = event.getActivity();

            //If the current time is higher than the last Time Point: moving to new time point:
            if (t > time[lastTP] && t <= maxEnd.value()) {
                // If the required consumption overloads the maximum capacity and the available production: failure
                if (nOverlap[lastTP] > 0 && (maxCapacity < profileMin[lastTP] || profileMax[lastTP] < minCapacity))
                    throw new InconsistencyException();
                lastTP++;
                resetTP(lastTP, t, profileMin[lastTP - 1], profileMax[lastTP - 1], nOverlap[lastTP - 1]);
            }

            switch (event.getType()) {
                case START_MIN -> {
                    profileMin[lastTP] += Math.min(act.getHeightMin(), 0);
                    profileMax[lastTP] += Math.max(act.getHeightMax(), 0);
                    actToStartMinTp[event.getActIdx()] = lastTP;
                }
                case END_MAX -> {
                    profileMin[lastTP] -= Math.min(act.getHeightMin(), 0);
                    profileMax[lastTP] -= Math.max(act.getHeightMax(), 0);
                    actToEndMaxTp[event.getActIdx()] = lastTP;
                }
                case START_MAX -> {
                    profileMin[lastTP] += Math.max(act.getHeightMin(), 0);
                    profileMax[lastTP] += Math.min(act.getHeightMax(), 0);
                    nOverlap[lastTP]++;
                }
                case END_MIN -> {
                    profileMin[lastTP] -= Math.max(act.getHeightMin(), 0);
                    profileMax[lastTP] -= Math.min(act.getHeightMax(), 0);
                    nOverlap[lastTP]--;
                }
            }
        }
    }

    //Timetabling filtering:
    protected void timeTabling(int n) {
        for (int i = 0; i < n; i++) {
            int actIdx = active[i];
            Activity act = activities[actIdx];
            if (!act.isFixed()) {
                long maxH = Integer.MIN_VALUE; //Maximum available height over activity window
                long minH = Integer.MAX_VALUE; //Minimum available height over activity window

                //Forward check until fixed part or end min of activity:
                int tpForward = actToStartMinTp[actIdx];
                while (time[tpForward] < Math.min(act.getStartMax(), act.getEndMin())) {
                    // if by forcing the task, the profile does not intersect the capacity range
                    // then the task is pushed to the next time point:
                    if ((profileMin[tpForward] + Math.max(act.getHeightMin(), 0) > maxCapacity) ||
                            (profileMax[tpForward] + Math.min(act.getHeightMax(), 0) < minCapacity)
                    ) {
                        act.setStartMin(getEnd(tpForward));
                    } else {
                        if (mandatoryActive) checkIfMandatory(actIdx, tpForward); //Checking if activity is mandatory
                        //Updating min & max available heights:
                        maxH = Math.max(maxH, maxCapacity - ((long) profileMin[tpForward] - Math.min(act.getHeightMin(), 0L)));
                        minH = Math.min(minH, minCapacity - ((long) profileMax[tpForward] - Math.max(act.getHeightMax(), 0L)));
                    }
                    tpForward++;
                }

                //Backward check until fixed part or start max of activity:
                int tpBackward = actToEndMaxTp[actIdx] - 1;
                while (tpBackward >= 0 && getEnd(tpBackward) > Math.max(act.getEndMin(), act.getStartMax())) {
                    // if by forcing the task, the profile does not intersect the capacity range
                    // then the task is pushed to the previous time point:
                    if ((profileMin[tpBackward] + Math.max(act.getHeightMin(), 0) > maxCapacity) ||
                            (profileMax[tpBackward] + Math.min(act.getHeightMax(), 0) < minCapacity)
                    ) {
                        act.setEndMax(time[tpBackward]);
                    } else {
                        if (mandatoryActive) checkIfMandatory(actIdx, tpBackward); //Checking if activity is mandatory
                        //Updating min & max available heights:
                        maxH = Math.max(maxH, maxCapacity - ((long) profileMin[tpBackward] - Math.min(act.getHeightMin(), 0L)));
                        minH = Math.min(minH, minCapacity - ((long) profileMax[tpBackward] - Math.max(act.getHeightMax(), 0L)));
                    }
                    tpBackward--;
                }

                if (!simpleCumulative) {
                    if (act.hasFixedPart()) {
                        //Checking fixed part of activity:
                        while (!act.isAbsent() && time[tpForward] < act.getEndMin()) {
                            if (mandatoryActive)
                                checkIfMandatory(actIdx, tpForward); //Checking if activity is mandatory
                            // Adjusting height:
                            // (Necessary even if height is fixed as height adjustment will remove task if not possible)
                            // adjustHeightOnFixedPart(actIdx, tpForward);
                            tpForward++;
                        }
                    } else {
                        // Last time at which the act can start and span until now without obstruction:
                        // (used to compute max length)
                        int currentStart = act.getStartMin();
                        int maxL = 0; //Current maximum length

                        //Checking free part of activity:
                        while (!act.isAbsent() && time[tpForward] < act.getStartMax()) {
                            maxL = Math.max(maxL, time[tpForward] - currentStart); //Updating max length
                            //If obstruction, moving current start to next time point:
                            if ((profileMin[tpForward] + Math.max(act.getHeightMin(), 0) > maxCapacity) ||
                                    (profileMax[tpForward] + Math.min(act.getHeightMax(), 0) < minCapacity)
                            ) currentStart = getEnd(tpForward);
                            else if (mandatoryActive)
                                checkIfMandatory(actIdx, tpForward); //Checking if activity is mandatory
                            //Updating min & max available heights:
                            maxH = Math.max(maxH, maxCapacity - ((long) profileMin[tpForward] - Math.min(act.getHeightMin(), 0L)));
                            minH = Math.min(minH, minCapacity - ((long) profileMax[tpForward] - Math.max(act.getHeightMax(), 0L)));
                            tpForward++;
                        }
                        //Adjusting maximum length:
                        maxL = Math.max(maxL, act.getEndMax() - currentStart);
                        // act.setLengthMax(Math.min(act.getLengthMax(), maxL));

                        // Adjusting height:
                        // (Necessary even if height is fixed as height adjustment will remove task if not possible)
                        // act.setHeightMax((int) Math.min(act.getHeightMax(), maxH));
                        // act.setHeightMin((int) Math.max(act.getHeightMin(), minH));
                    }
                }
            }
        }
    }

    // Checks if the activity is mandatory:
    // if by not scheduling the task, the profile does not intersect the capacity range
    // then the task is forced to span the time point
    protected void checkIfMandatory(int actIdx, int tp) {
        Activity act = activities[actIdx];
        long minCapaDeficit = minCapacity - ((long) profileMax[tp] - Math.max(act.getHeightMax(), 0L) - (isIncludedInProfileAt(actIdx, time[tp]) ? Math.min(act.getHeightMax(), 0L) : 0L));
        long maxCapaOverload = maxCapacity - ((long) profileMin[tp] - Math.min(act.getHeightMin(), 0L) - (isIncludedInProfileAt(actIdx, time[tp]) ? Math.max(act.getHeightMin(), 0L) : 0L));

        // If the task is detected mandatory:
        if (nOverlap[tp] > 0 && (minCapaDeficit > 0 || maxCapaOverload < 0)) {
            // Ensure it is run during the current time point:
            act.setPresent();
            act.setStartMax(time[tp]);
            act.setEndMin(getEnd(tp));
            // (length min, start min and end max are also updated when setting start max and end min).

            // Ensure the minimum or maximum height of the task covers the profile deficit or overload:
            if (minCapaDeficit > 0) act.setHeightMin((int) Math.max(minCapaDeficit, act.getHeightMin()));
            if (maxCapaOverload < 0) act.setHeightMax((int) Math.min(maxCapaOverload, act.getHeightMax()));
        }
    }

    // if profileMax > maxCapacity and we are on a fixed-part of the task, we can reduce its height
    // perform computation on longs to avoid overflow
    protected void adjustHeightOnFixedPart(int actIdx, int tp) {
        Activity act = activities[actIdx];
        if (act.hasFixedPartAt(time[tp])) {
            long minH = minCapacity - ((long) profileMax[tp] - Math.max(act.getHeightMax(), 0L) - (isIncludedInProfileAt(actIdx, time[tp]) ? Math.min(act.getHeightMax(), 0L) : 0L));
            long maxH = maxCapacity - ((long) profileMin[tp] - Math.min(act.getHeightMin(), 0L) - (isIncludedInProfileAt(actIdx, time[tp]) ? Math.max(act.getHeightMin(), 0L) : 0L));
            act.setHeightMin((int) Math.max(minH, act.getHeightMin()));
            act.setHeightMax((int) Math.min(maxH, act.getHeightMax()));
        }
    }

    //Returns end time of time point:
    public int getEnd(int idx) {
        return idx < lastTP ? time[idx + 1] : time[idx];
    }

    // Resets time point at given index with provided values
    public void resetTP(int idx, int time, int profileMin, int profileMax, int nOverlap) {
        this.time[idx] = time;
        this.profileMin[idx] = profileMin;
        this.profileMax[idx] = profileMax;
        this.nOverlap[idx] = nOverlap;
    }

    //Checks if a task fixed part is included in the profile at given time
    public boolean isIncludedInProfileAt(int tsk, int time) {
        return isPresentInProfile[tsk] && startMaxInProfile[tsk] <= time && endMinInProfile[tsk] > time;
    }

    public enum eventType {
        START_MIN,
        START_MAX,
        END_MIN,
        END_MAX
    }

    /**
     * Represents a task event. Used to compute profile
     */
    public class Event {
        private int act;
        private eventType type;

        public Event(int act, eventType type) {
            this.act = act;
            this.type = type;
        }

        public int getActIdx() {
            return act;
        }

        public Activity getActivity() {
            return activities[act];
        }

        public eventType getType() {
            return type;
        }

        public int getTime() {
            Activity tsk = getActivity();
            switch (type) {
                case START_MAX -> {
                    return tsk.getStartMax();
                }
                case END_MIN -> {
                    return tsk.getEndMin();
                }
                case END_MAX -> {
                    return tsk.getEndMax();
                }
                default -> {
                    return tsk.getStartMin();
                }
            }
        }

        public void reset(int act, eventType type) {
            this.act = act;
            this.type = type;
        }

        public String toString() {
            return "(Time : " + this.getTime() + " Type : " + this.type + ")";
        }
    }
}