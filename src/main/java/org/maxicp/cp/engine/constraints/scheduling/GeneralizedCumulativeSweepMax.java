package org.maxicp.cp.engine.constraints.scheduling;


import org.maxicp.Constants;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class GeneralizedCumulativeSweepMax extends AbstractCPConstraint {
    private final Activity[] activities;
    private final int maxCapacity;

    //Propagation structures:
    private final int nMax;

    private final Event[] eventPointSeries;
    private int nbEvents; // current size of events
    private int delta; // current position of the sweep line
    private int capaSumHeight; // sum of the height of the tasks that overlap the sweep line
    private int nbTasksToPrune; // number of tasks in stackPrune


    private final int[] stackPrune; // Tasks that could intersect the sweep line
    private final int[] capaContrib; // contribution of all the tasks that are added to capaSumHeight

    private final EventList[] eventList; // contains all the possible events of each task (used for speed-up)

    public GeneralizedCumulativeSweepMax(List<Activity> activities, int maxCapacity) {
        this(activities.toArray(new Activity[0]), maxCapacity);
    }

    public GeneralizedCumulativeSweepMax(Activity [] activities, int maxCapacity) {
        super(activities[0].interval().getSolver());
        if (maxCapacity < 0) throw new IllegalArgumentException("The maximum capacity provided is < 0");
        this.activities = activities.clone();
        this.maxCapacity = maxCapacity;

        nMax = this.activities.length;

        this.nbEvents = 0;
        this.delta = 0;
        this.capaSumHeight = 0;
        this.nbTasksToPrune = 0;
        this.stackPrune = new int[nMax];
        this.capaContrib = new int[nMax];
        this.eventPointSeries = new Event[3 * nMax];
        this.eventList = new EventList[nMax];
        for (int i = 0; i < nMax; i++) {
            eventList[i] = new EventList(i);
        }
    }

    private boolean generateProfileBad(int t) {
        if (activities[t].height().min() > 0) {
            eventPointSeries[nbEvents] = eventList[t].sBadProfile(activities[t].height().min());
            nbEvents ++;
            eventPointSeries[nbEvents] = eventList[t].eBadProfile(activities[t].height().min());
            nbEvents ++;
            return true;
        }
        return false;
    }

    private boolean generateProfileGood(int t) {
        if (activities[t].height().min() < 0) {
            eventPointSeries[nbEvents] = eventList[t].sGoodProfile(activities[t].height().min());
            nbEvents ++;
            eventPointSeries[nbEvents] = eventList[t].eGoodProfile(activities[t].height().min());
            nbEvents ++;
            return true;
        }
        return false;
    }


    private boolean consistencyCheck() {
        return (capaSumHeight > maxCapacity);
    }
    private boolean mandatoryCheck(int t) {
        return ((capaSumHeight - capaContrib[t]) > maxCapacity);
    }
    private boolean forbidenCheck(int t) {
        return (capaSumHeight - capaContrib[t] + activities[t].height().min() > maxCapacity);
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
        //Propagation:
        // Generates events
        if (!generateEventPointSeries())
            return;
        // Performs a sweep on the events
        sweepAlgorithm();
    }

    private boolean generateEventPointSeries() {
        // True if a profile event has been generated
        boolean profileEvent = false;
        // Reset eventPointSeries
        nbEvents = 0;
        int i = 0;
        while (i < nMax) {
            if (activities[i].interval().startMax() < activities[i].interval().endMin() && activities[i].interval().isPresent()) {
                // Profile (Bad : on compulsory part)
                profileEvent |= generateProfileBad(i);
            }
            // Profile (Good : on entire domain)
            if (!activities[i].interval().isAbsent())
                profileEvent |= generateProfileGood(i);
            // Pruning (if something is not fixed)
            if (!activities[i].interval().isAbsent() && (!activities[i].interval().isFixed() || !activities[i].height().isFixed())) {
                eventPointSeries[nbEvents] = eventList[i].sPruning();
                nbEvents++;
            }
            i ++;
        }
        return profileEvent;
    }

    private void resetSweepLine() {
        delta = 0;
        capaSumHeight = 0;
        nbTasksToPrune = 0;
        for (int i = 0; i < nMax; i++) {
            capaContrib[i] = 0;
        }
    }

    private void sweepAlgorithm() {
        resetSweepLine();
        // Sort events by increasing date
        Arrays.sort(eventPointSeries, 0, nbEvents, Comparator.comparing(Event::date));
        // First position of the sweep line
        delta = eventPointSeries[0].date();
        int i = 0;
        while (i < nbEvents) {
            Event event = eventPointSeries[i];
            if (event.eType() != EventType.PRUNING) {
                // If we have considered all the events at the previous position
                // of the sweep line
                if (delta != event.date()) {
                    // Consistency check
                    if (consistencyCheck())
                        throw new InconsistencyException();
                    // Pruning (this could reduce the size of stackPrune)
                    prune(delta, event.date() - 1);
                    // Moves the sweep line
                    delta = event.date();
                }
                if (event.eType() == EventType.PROFILE) {
                    // Adjusts height capacity
                    capaSumHeight += event.height();
                    capaContrib[event.task()] += event.height();
                }
            } else  {
                stackPrune[nbTasksToPrune] = event.task();
                nbTasksToPrune += 1;
            }
            i++;
        }
        // Checks consistency
        if (consistencyCheck())
            throw new InconsistencyException();

        // Final pruning
        prune(delta, delta);
    }

    private void prune(int low, int up) {
        // Used to adjust stackPrune
        int nbRemainingTasksToPrune = 0;
        int i = 0;
        while (i < nbTasksToPrune) {
            int t = stackPrune[i];
            // Pruning on tasks that must be discarded to respect consistency
            pruneForbiden(t, low, up);
            // Pruning on tasks that are mandatory to respect consistency
            pruneMandatory(t, low, up);
            // Adjusts the height's consumption of the tasks
            pruneConsumption(t, low, up);
            // If the task is still in conflict, we keep it
            if (!(activities[t].interval().endMax() <= up + 1)) {
                stackPrune[nbRemainingTasksToPrune] = t;
                nbRemainingTasksToPrune += 1;
            }
            i ++;
        }
        // Adjusting stackPrune
        nbTasksToPrune = nbRemainingTasksToPrune;
    }

    private void pruneMandatory(int t, int low, int up) {
        // Checks if the task is mandatory to respect consistency
        if (!mandatoryCheck(t))
            return;
        // Adjust the status of the activity
        activities[t].interval().setPresent();
        // Adjust the EST of the activity
        activities[t].interval().setStartMin(up - activities[t].interval().lengthMax() + 1);

        // Adjust the LST of the activity
        activities[t].interval().setStartMax(low);

        // Adjust the LCT of the activity
        activities[t].interval().setEndMax(low + activities[t].interval().lengthMax());

        // Adjust the ECT of the activity
        activities[t].interval().setEndMin(up + 1);

        // Adjust the minimal duration of the activity
        activities[t].interval().setLengthMin(Math.min(up - activities[t].interval().startMax() + 1, activities[t].interval().endMin() - low));

    }



    private void pruneForbiden(int t, int low, int up) {
        // Checks if the task must be discarded to respect consistency
        if (forbidenCheck(t)) {
            if (activities[t].interval().endMin() <= low || activities[t].interval().startMax() > up || activities[t].interval().lengthMin() <= 0) {
                if (activities[t].interval().lengthMin() > 0) {
                    pruneIntervalStart(low - activities[t].interval().lengthMin() + 1, up, activities[t]);
                    pruneIntervalEnd(low + 1, up + activities[t].interval().lengthMin(), activities[t]);
                }
                int maxD = Math.max(Math.max(low - activities[t].interval().startMin(), activities[t].interval().endMax() - up - 1), 0);
                activities[t].interval().setLengthMax(maxD);
            }
        }
    }

    private void pruneConsumption(int t, int low, int up) {
        if (activities[t].interval().endMin() > low && activities[t].interval().startMax() <= up && activities[t].interval().lengthMin() > 0) {
            int v = maxCapacity - (capaSumHeight - capaContrib[t]);
            activities[t].height().removeAbove(maxCapacity - (capaSumHeight - capaContrib[t]));
        }
    }

    private void pruneIntervalStart(int low, int up, Activity v) {
        assert(low <= up);
        if (low <= v.interval().startMin() && up <= v.interval().startMax()) {
            v.interval().setStartMin(up + 1);
        } else if (up >= v.interval().startMax() && low >= v.interval().startMin()) {
            v.interval().setStartMax(low - 1);
        }
    }

    private void pruneIntervalEnd(int low, int up, Activity v) {
        assert(low <= up);
        if (low <= v.interval().endMin() && up <= v.interval().endMax()) {
            v.interval().setEndMin(up + 1);
        } else if (up >= v.interval().endMax() && low >= v.interval().endMin()) {
            v.interval().setEndMax(low - 1);
        }
    }


    public enum EventType {
        CHECK, PROFILE, PRUNING;
    }

    public static class Event {
        EventType type;
        int task;
        int time;
        int height;
        public Event(EventType type, int task, int time, int height) {
            this.type = type;
            this.task = task;
            this.time = time;
            this.height = height;
        }
        private int date(){ return time;}
        private EventType eType() { return type;}
        private int height() {return height;}
        private int task() {return task; }
        public String toString() {
            return "type " + type + " date " + time + " task " + task;
        }
    }

    public class EventList {
        private Event sCheckEvent;
        private Event eCheckEvent;
        private Event sBadProfileEvent;
        private Event eBadProfileEvent;
        private Event sGoodProfileEvent;
        private Event eGoodProfileEvent;
        private Event pruningEvent;

        public EventList(int t) {
            this.sCheckEvent = new Event(EventType.CHECK, t, 0, 1);
            this.eCheckEvent = new Event(EventType.CHECK, t, 0, -1);
            this.sBadProfileEvent = new Event(EventType.PROFILE, t, 0, 0);
            this.eBadProfileEvent = new Event(EventType.PROFILE, t, 0, 0);
            this.sGoodProfileEvent = new Event(EventType.PROFILE, t, 0, 0);
            this.eGoodProfileEvent = new Event(EventType.PROFILE, t, 0, 0);
            this.pruningEvent = new Event(EventType.PRUNING, t, 0, 0);
        }
        private Event sCheck() {
            sCheckEvent.time = activities[sCheckEvent.task].interval().startMax();
            return sCheckEvent;
        }
        private Event eCheck() {
            eCheckEvent.time = activities[eCheckEvent.task].interval().endMin();
            return eCheckEvent;
        }
        private Event sBadProfile(int height) {
            sBadProfileEvent.time = activities[sBadProfileEvent.task].interval().startMax();
            sBadProfileEvent.height = height;
            return sBadProfileEvent;
        }

        private Event eBadProfile(int height) {
            eBadProfileEvent.time = activities[eBadProfileEvent.task].interval().endMin();
            eBadProfileEvent.height = -height;
            return eBadProfileEvent;
        }
        private Event sGoodProfile(int height) {
            sGoodProfileEvent.time = activities[sGoodProfileEvent.task].interval().startMin();
            sGoodProfileEvent.height = height;
            return sGoodProfileEvent;
        }
        private Event eGoodProfile(int height) {
            eGoodProfileEvent.time = activities[eGoodProfileEvent.task].interval().endMax();
            eGoodProfileEvent.height = -height;
            return eGoodProfileEvent;
        }
        private Event sPruning() {
            pruningEvent.time = activities[pruningEvent.task].interval().startMin();
            return pruningEvent;
        }
    }
}
