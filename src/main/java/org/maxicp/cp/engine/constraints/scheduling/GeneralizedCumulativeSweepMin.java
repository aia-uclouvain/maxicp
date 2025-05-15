package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.Constants;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.modeling.algebra.integer.Constant;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class GeneralizedCumulativeSweepMin extends AbstractCPConstraint {
    private final Activity[] activities;
    private final int minCapacity;

    //Propagation structures:
    private final int nMax;

    private final Event[] eventPointSeries;
    private int nbEvents; // current size of events
    private int delta; // current position of the sweep line
    private int consSumHeight; // sum of the height of the tasks that overlap the sweep line
    private int nbCurrentTasks; // number of tasks that overlap the sweep line
    private int nbTasksToPrune; // number of tasks in stackPrune


    private final int[] stackPrune; // Tasks that could intersect the sweep line
    private final int[] consContrib; // contribution of all the tasks that are added to consSumHeight

    private final EventList[] eventList; // contains all the possible events of each task (used for speed-up)

    public GeneralizedCumulativeSweepMin(List<Activity> activities, int minCapacity) {
        this(activities.toArray(new Activity[0]), minCapacity);
    }

    public GeneralizedCumulativeSweepMin(Activity[] activities, int minCapacity) {
        super(activities[0].interval().getSolver());
        if (minCapacity < 0) throw new IllegalArgumentException("The minimum capacity provided is < 0");
        this.activities = activities.clone();
        this.minCapacity = minCapacity;

        nMax = this.activities.length;

        this.nbEvents = 0;
        this.delta = 0;
        this.consSumHeight = 0;
        this.nbCurrentTasks = 0;
        this.nbTasksToPrune = 0;
        this.stackPrune = new int[nMax];
        this.consContrib = new int[nMax];
        this.eventPointSeries = new Event[5 * nMax];
        this.eventList = new EventList[nMax];
        for (int i = 0; i < nMax; i++) {
            eventList[i] = new EventList(i);
        }
    }

    private void generateCheck(int t) {
        if (activities[t].height().max() < minCapacity) {
            eventPointSeries[nbEvents] = eventList[t].sCheck();
            nbEvents ++;
            eventPointSeries[nbEvents] = eventList[t].eCheck();
            nbEvents ++;
        }
    }

    private boolean generateProfileBad(int t) {
        if (activities[t].height().max() < 0) {
            eventPointSeries[nbEvents] = eventList[t].sBadProfile(activities[t].height().max());
            nbEvents ++;
            eventPointSeries[nbEvents] = eventList[t].eBadProfile(activities[t].height().max());
            nbEvents ++;
            return true;
        }
        return false;
    }

    private boolean generateProfileGood(int t) {
        if (activities[t].height().max() > 0) {
            eventPointSeries[nbEvents] = eventList[t].sGoodProfile(activities[t].height().max());
            nbEvents ++;
            eventPointSeries[nbEvents] = eventList[t].eGoodProfile(activities[t].height().max());
            nbEvents ++;
            return true;
        }
        return false;
    }


    private boolean consistencyCheck() {
        return (nbCurrentTasks > 0 && consSumHeight < minCapacity);
    }
    private boolean mandatoryCheck(int t) {
        return (nbCurrentTasks != 0 && (consSumHeight - consContrib[t]) < minCapacity);
    }
    private boolean forbidenCheck(int t) {
        return (consSumHeight - consContrib[t] + activities[t].height().max() < minCapacity);
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
                // check
                generateCheck(i);
                // Profile (Bad : on compulsory part)
                profileEvent |= generateProfileBad(i);
            }
            // Profile (Good : on entire domain)
            if (!activities[i].interval().isAbsent()) {
                profileEvent |= generateProfileGood(i);
            }
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
        consSumHeight = 0;
        nbCurrentTasks = 0;
        nbTasksToPrune = 0;
        for (int i = 0; i < nMax; i++) {
            consContrib[i] = 0;
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
                    // Adjusts height consumption
                    consSumHeight += event.cons();
                    consContrib[event.task()] += event.cons();
                } else if (event.eType() == EventType.CHECK) {
                    // Number of overlapping tasks
                    nbCurrentTasks += event.cons();
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
        if (activities[t].isPresent() && activities[t].interval().endMin() > low && activities[t].interval().startMax() <= up && activities[t].interval().lengthMin() > 0) {
            activities[t].height().removeBelow(minCapacity - (consSumHeight - consContrib[t]));
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
        if (v.interval().endMax() == Constants.HORIZON) {
            if (low >= v.interval().endMin()) {
                v.interval().setEndMax(low - 1);
            }
        }
    }


    public enum EventType {
        CHECK, PROFILE, PRUNING;
    }

    public static class Event {
        EventType e;
        int t;
        int d;
        int conso;
        int cap;
        public Event(EventType e, int t, int d, int conso) {
            this.e = e;
            this.t = t;
            this.d = d;
            this.conso = conso;
        }
        private int date(){ return d;}
        private EventType eType() { return e;}
        private int cons() { return conso;}
        private int task() {return t; }
        public String toString() {
            return "type " + e + " date "+ d + " task " + t + " cons " + conso;
        }
    }
    public class EventList {
        private int t;
        private Event sCheckEvent;
        private Event eCheckEvent;
        private Event sBadProfileEvent;
        private Event eBadProfileEvent;
        private Event sGoodProfileEvent;
        private Event eGoodProfileEvent;
        private Event pruningEvent;

        public EventList(int t) {
            this.t = t;
            this.sCheckEvent = new Event(EventType.CHECK, t, 0, 1);
            this.eCheckEvent = new Event(EventType.CHECK, t, 0, -1);
            this.sBadProfileEvent = new Event(EventType.PROFILE, t, 0, 0);
            this.eBadProfileEvent = new Event(EventType.PROFILE, t, 0, 0);
            this.sGoodProfileEvent = new Event(EventType.PROFILE, t, 0, 0);
            this.eGoodProfileEvent = new Event(EventType.PROFILE, t, 0, 0);
            this.pruningEvent = new Event(EventType.PRUNING, t, 0, 0);
        }

        private Event sCheck() {
            sCheckEvent.d = activities[sCheckEvent.t].interval().startMax();
            return sCheckEvent;
        }
        private Event eCheck() {
            eCheckEvent.d = activities[eCheckEvent.t].interval().endMin();
            return eCheckEvent;
        }

        private Event sBadProfile(int cons_) {
            sBadProfileEvent.d = activities[sBadProfileEvent.t].interval().startMax();
            sBadProfileEvent.conso = cons_;
            return sBadProfileEvent;
        }
        private Event eBadProfile(int cons_) {
            eBadProfileEvent.d = activities[eBadProfileEvent.t].interval().endMin();
            eBadProfileEvent.conso = -cons_;
            return eBadProfileEvent;
        }

        private Event sGoodProfile(int cons_) {
            sGoodProfileEvent.d = activities[sGoodProfileEvent.t].interval().startMin();
            sGoodProfileEvent.conso = cons_;
            return sGoodProfileEvent;
        }

        private Event eGoodProfile(int cons_) {
            eGoodProfileEvent.d = activities[eGoodProfileEvent.t].interval().endMax();
            eGoodProfileEvent.conso = -cons_;
            return eGoodProfileEvent;
        }
        private Event sPruning() {
            pruningEvent.d = activities[pruningEvent.t].interval().startMin();
            return pruningEvent;
        }
    }
}
