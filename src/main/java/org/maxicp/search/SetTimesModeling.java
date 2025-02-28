package org.maxicp.search;

import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;
import org.maxicp.state.datastructures.StateSparseSet;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.OptionalInt;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.maxicp.modeling.Factory.start;
import static org.maxicp.search.Searches.EMPTY;
import static org.maxicp.search.Searches.branch;

public class SetTimesModeling implements Supplier<Runnable[]> {

    private final IntervalVar[] intervals;
    private final IntFunction<Integer> tieBreaker;

    private final int nTasks;
    private final int[] unassignedIterator;
    private final StateSparseSet selectableTasks;
    private final StateInt[] oldEst;
    private final ModelProxy model;

    public SetTimesModeling(IntervalVar[] intervals, IntFunction<Integer> tieBreaker) {
        nTasks = intervals.length;
        this.intervals = intervals;
        this.tieBreaker = tieBreaker;
        this.model = intervals[0].getModelProxy();
        StateManager sm = model.getConcreteModel().getStateManager();
        selectableTasks = new StateSparseSet(sm, nTasks, 0);
        unassignedIterator = new int[nTasks];
        this.oldEst = Arrays.stream(new int[nTasks]).mapToObj(i -> sm.makeStateInt(Integer.MIN_VALUE)).toArray(StateInt[]::new);
    }

    public SetTimesModeling(IntervalVar... intervals) {
        this(intervals, i -> i);
    }

    @Override
    public Runnable[] get() {
        // select task with minimum est
        OptionalInt taskCandidate = validTaskWithMinEst();
        if (taskCandidate.isEmpty()) { // no task suitable for selection
            return EMPTY;
        }
        int task = taskCandidate.getAsInt();
        int est = intervals[task].startMin();
        if (existsPostponedForever(est)) {
            throw InconsistencyException.INCONSISTENCY;
        } else {
            IntervalVar taskInterval = intervals[task];
            int lct = taskInterval.endMax();
            // select the minimum ect of a task that is not current one, and has an ect greater than lct
            // we just fix it since no other tasks are competing for this time
            int minEct = getMinEct(est, task);
            if (minEct >= lct) {
                return branch(() -> {
                    model.add(start(taskInterval, est));
                    selectableTasks.remove(task);
                });
            } else {
                return branch(() -> {
                    // start = est
                    model.add(start(taskInterval, est));
                    selectableTasks.remove(task);
                    dominanceCheck();
                }, () -> {
                    // postpone the task
                    model.fixpoint();
                    oldEst[task].setValue(est);
                    dominanceCheck();
                });
            }
        }
    }

    private void dominanceCheck() {
        if (!selectableTasks.isEmpty()) {
            int nUnassigned = selectableTasks.fillArray(unassignedIterator);
            boolean failed = true;
            for (int i = 0; i < nUnassigned && failed; i++) {
                int task = unassignedIterator[i];
                failed = isPostponed(task) /*&& durations[taskId].max() != 0*/;
            }
            if (failed) {
                throw InconsistencyException.INCONSISTENCY;
            }
        }
    }

    private int getMinEct(int value, int forbiddenTask) {
        int minEct = Integer.MAX_VALUE;
        for (int task = 0 ; task < nTasks ; task++) {
            if (task != forbiddenTask) {
                int ect = intervals[task].endMin();
                if (ect < minEct && ect > value) {
                    minEct = ect;
                }
            }
        }
        return minEct;
    }

    /**
     * Selects the non-postponed task with minimum earliest start time
     * @return non-postponed task with minimum earliest start time
     */
    private OptionalInt validTaskWithMinEst() {
        int nTasks = selectableTasks.fillArray(unassignedIterator);
        if (nTasks == 0)
            return OptionalInt.empty();
        int bestTask = -1;
        int minEst = Integer.MAX_VALUE;
        int minTie = Integer.MAX_VALUE;
        for (int i = 0 ; i < nTasks ; i++) {
            int task = unassignedIterator[i];
            if (intervals[task].isFixed()) {
                selectableTasks.remove(task);
            } else {
                if (!isPostponed(task)) {
                    int est = intervals[task].startMin();
                    if (est < minEst) {
                        minEst = est;
                        minTie = tieBreaker.apply(task);
                        bestTask = task;
                    } else if (est == minEst) {
                        int tie = tieBreaker.apply(task);
                        if (tie < minTie) {
                            minTie = tie;
                            bestTask = task;
                        }
                    }
                }
            }
        }
        if (bestTask == -1) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(bestTask);
        }
    }

    /**
     * Returns true if there exists at least one postponed activity that cannot be scheduled given the current est
     * @param currentEst current earliest start time, used to detect if an activity will always be postponed
     * @return true if an activity will always be postponed given the current est
     */
    private boolean existsPostponedForever(int currentEst) {
        int nUnassigned = selectableTasks.fillArray(unassignedIterator);
        for (int i = 0; i < nUnassigned; i++) {
            int task = unassignedIterator[i];
            if (isPostponed(task)) {
                // Once an activity is postponed, it must always be scheduled after the current est.
                // Since the currentEst can only increase down a branch,
                // ends(taskId).min <= est implies that taskId will remain postponed forever.
                // starts(taskId).max <= est implies that it would never be possible to schedule taskId after est.
                // If one of these two cases occurs, we can safely fail.
                if (intervals[task].endMin() <= currentEst || intervals[task].startMax() <= currentEst) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPostponed(int task) {
        return oldEst[task].value() >= intervals[task].startMin();
    }

}
