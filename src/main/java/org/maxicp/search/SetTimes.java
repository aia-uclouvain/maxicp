/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.search;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.state.StateInt;
import org.maxicp.state.datastructures.StateSparseSet;
import org.maxicp.util.exception.InconsistencyException;

import java.util.function.IntFunction;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Set Times Branching
 */
public class SetTimes {

    public static Supplier<Runnable[]> setTimes(CPIntervalVar [] intervals, IntFunction<Integer> tieBreaker) {
        SetTimes setTimes = new SetTimes(intervals, tieBreaker);
        return () -> setTimes.alternatives().toArray(new Runnable[0]);
    }

    private final CPIntervalVar[] intervals;
    private final IntFunction<Integer> tieBreaker;
    private final CPSolver cp;
    private final int nTasks;
    private final int[] unassignedIterator;
    private final StateSparseSet unassigned;
    private final StateInt [] oldEst;

    public SetTimes(CPIntervalVar [] intervals, IntFunction<Integer> tieBreaker) {
        this.intervals = intervals;
        this.tieBreaker = tieBreaker;
        this.cp = intervals[0].getSolver();
        this.nTasks = intervals.length;
        this.unassigned = new StateSparseSet(cp.getStateManager(),nTasks,0);
        this.unassignedIterator = new int[nTasks];
        this.oldEst = Arrays.stream(new int[nTasks]).mapToObj(i -> cp.getStateManager().makeStateInt(Integer.MIN_VALUE)).toArray(StateInt[]::new);
    }

    public List<Runnable> alternatives() {
        if (unassigned.isEmpty()) {
            return List.of(); // noAlternative equivalent
        } else {
            int taskId = selectTask(); // task with min est
            CPIntervalVar start = intervals[taskId];
            int est = start.startMin();
            if (existsPostponedForever(est)) {
                throw InconsistencyException.INCONSISTENCY;
            } else {
                int lct = intervals[taskId].endMax();
                // select the minimum ect of a task that is not taskId and has an ect greater than lct,
                // we just fix it since no other tasks are competing for this time
                if (selectMinEct(est, taskId) >= lct) {
                    return List.of(() -> {
                        cp.post(CPFactory.startAt(start, est));
                        unassigned.remove(taskId);
                    });
                } else {
                    return List.of(
                            () -> { // left branch: start = est
                                cp.post(CPFactory.startAt(start, est));
                                unassigned.remove(taskId);
                                dominanceCheck();
                            },
                            () -> {
                                // right branch: postpone the task
                                cp.fixPoint();
                                oldEst[taskId].setValue(est);
                                dominanceCheck();
                            }
                    );
                }
            }
        }
    }


    private int selectTask() {
        int minTask = -1;
        int minEst = Integer.MAX_VALUE;
        int minTie = Integer.MAX_VALUE;

        int nUnassigned = unassigned.fillArray(unassignedIterator); // fill the iterator with the unassigned tasks
        for (int i = 0; i < nUnassigned; i++) {
            int taskId = unassignedIterator[i];
            int est = intervals[taskId].startMin();
            // select a task that is not currently postponed
            if (oldEst[taskId].value() < est /*|| durations[taskId].max() == 0*/) {
                if (est < minEst) {
                    minTask = taskId;
                    minEst = est;
                    minTie = tieBreaker.apply(taskId);
                } else if (est == minEst) {
                    int tie = tieBreaker.apply(taskId);
                    if (tie < minTie) {
                        minTask = taskId;
                        minTie = tie;
                    }
                }
            }

        }
        return minTask; // -1 indicates no task was selected
    }

    private int selectMinEct(int value, int taskId) {
        int task = nTasks;
        int minEct = Integer.MAX_VALUE;
        while (task > 0) {
            task--;
            if (task != taskId) {
                int ect = intervals[task].endMin();
                if (ect < minEct && ect > value) {
                    minEct = ect;
                }
            }
        }
        return minEct;
    }

    private boolean existsPostponedForever(int currentEst) {
        int nUnassigned = unassigned.fillArray(unassignedIterator);
        for (int i = 0; i < nUnassigned; i++) {
            int taskId = unassignedIterator[i];
            if (oldEst[taskId].value() >= intervals[taskId].startMin() /*&& durations[taskId].max() != 0*/) {
                if (intervals[taskId].endMin() <= currentEst || intervals[taskId].startMax() <= currentEst) {
                    return true;
                }
                // Once an activity is postponed, it must always be scheduled after the current est.
                // Since the currentEst can only increase down a branch,
                // ends(taskId).min <= est implies that taskId will remain postponed forever.
                // starts(taskId).max <= est implies that it would never be possible to schedule taskId after est.
                // If one of these two cases occurs, we can safely fail.
            }
        }
        return false;
    }

    /**
     * If for all the unassigned tasks, the oldEst is greater than the min start, then we can safely fail.
     */
    private void dominanceCheck() {
        if (!unassigned.isEmpty()) {
            int nUnassigned = unassigned.fillArray(unassignedIterator);
            boolean failed = true;
            for (int i = 0; i < nUnassigned && failed; i++) {
                int taskId = unassignedIterator[i];
                failed = oldEst[taskId].value() >= intervals[taskId].startMin() /*&& durations[taskId].max() != 0*/;
            }
            if (failed) {
                throw InconsistencyException.INCONSISTENCY;
            }
        }
    }


    public boolean isInconsistent(Runnable callable) {
        try {
            callable.run();
            return false;
        } catch (InconsistencyException e) {
            return true;
        }
    }
}
