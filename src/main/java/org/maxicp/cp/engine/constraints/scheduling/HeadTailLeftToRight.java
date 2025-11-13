/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.maxicp.cp.CPFactory.makeIntervalVar;

public class HeadTailLeftToRight {

    public enum Outcome {
        NO_CHANGE, CHANGE, INCONSISTENCY
    }

    public final int[] startMin, endMax;
    private final int[] startMinNew, startMax, duration, endMin;
    int n;

    int nOps;
    Operation[] K;
    PriorityQueue<Operation> operations;
    PriorityQueue<Operation> pq;
    boolean[] delayed;
    int maxLct;
    HashMap<Integer, HashSet<Operation>> delayedBy;

    public HeadTailLeftToRight(int nMax) {
        startMin = new int[nMax];
        startMinNew = new int[nMax];
        startMax = new int[nMax];
        duration = new int[nMax];
        endMin = new int[nMax];
        endMax = new int[nMax];

        this.operations = new PriorityQueue<>(Comparator.comparingInt(a -> startMin[a.id]));
        this.K = new Operation[nMax];
        for (int i = 0; i < nMax; i++) {
            this.K[i] = new Operation(i, 0);
        }
        this.pq = new PriorityQueue<>(Comparator.comparingInt(a -> endMax[a.id]));
        this.delayed = new boolean[nMax];
        this.delayedBy = new HashMap<>();
    }

    /**
     * Applies all the filtering algorithms until a fix point is reached,
     * or an inconsistency is detected.
     *
     * @param startMin the minimum start time of each activity
     * @param duration the duration of each activity
     * @param endMax   the maximum end time of each activity
     * @param n        a number between 0 and startMin.length-1, is the number of activities to consider (prefix),
     *                 The other ones are just ignored
     * @return the outcome of the filtering, either NO_CHANGE, CHANGE or INCONSISTENCY.
     * If a change is detected, the time windows (startMin and endMax) are reduced.
     */
    public Outcome filter(int[] startMin, int[] duration, int[] endMax, int n) {
        if (n <= 1) {
            //return Outcome.NO_CHANGE;
        }
        update(startMin, duration, endMax, n);
        return fixPoint();
    }


    /**
     * @return false if an inconsistency is detected, true otherwise
     */
    private Outcome fixPoint() {
        Arrays.sort(K,0, n, Comparator.comparingInt((Operation op) -> endMax[op.id]).reversed());
        int t = 0;
        Outcome outcome = Outcome.NO_CHANGE;
        while (!operations.isEmpty() || !pq.isEmpty()) {
            ArrayList<Operation> cs = new ArrayList<>();
            // Add all operations released before or at time t
            // the operations are sorted by release time in the queue
            while (!operations.isEmpty() && startMin[operations.peek().id] <= t) {
                Operation op = operations.poll(); // remove from the queue
                if (delayed[op.id]) {
                    startMin[op.id]++;
                    if (startMin[op.id] > endMax[op.id]) {
                        return Outcome.INCONSISTENCY;
                    }
                    outcome = Outcome.CHANGE;
                    operations.add(op);
                } else {
                    if (startMin[op.id] == t) {
                        cs.add(op);
                    }
                    pq.offer(op);
                }
            }
            if (pq.isEmpty()) {
                // If nothing available, jump to next release time
                t = startMin[operations.peek().id];
                continue;
            }
            // cs is the set of operations available at time t
            for (Operation c : cs) {
                int pc = duration[c.id];
                int pplus = 0;
                ArrayList<Operation> Kc = new ArrayList<>();
                for (Operation op : K) {
                    if (op.id != c.id && op.p > 0) {
                        Kc.add(op);
                        pplus += op.p;
                    }
                }
                // Kc is the set of operations in K \ {c} with positive remaining processing time
                // Kc is sorted by decreasing endMax
                boolean alreadyDelayed = false;
                for (int i = 0; i < Kc.size(); i++) {
                    Operation sc = Kc.get(i);
                    if (sc.id != c.id) {
                        int psc = duration[sc.id];
                        if (!alreadyDelayed && startMin[c.id] + pc + pplus > endMax[sc.id]) {
                            startMin[c.id] += pplus;
                            if (startMin[c.id] + duration[c.id] > endMax[c.id]) {
                                return Outcome.INCONSISTENCY;
                            }
                            outcome = Outcome.CHANGE;
                            alreadyDelayed = true;
                            delayedBy.put(c.id, new HashSet<>(Kc.subList(i, Kc.size())));
                            delayed[c.id] = true;
                            pq.remove(c);
                            operations.add(c);
                        } else if (!alreadyDelayed) {
                            pplus -= sc.p;
                            if (sc.p < psc &&
                                    startMin[sc.id] + psc > startMin[c.id] &&
                                    startMin[c.id] + pc + psc > endMax[sc.id]) {
                                startMin[c.id] = startMin[c.id] + psc;
                                if (startMin[c.id] + duration[c.id] > endMax[c.id]) {
                                    return Outcome.INCONSISTENCY;
                                }
                                outcome = Outcome.CHANGE;
                                HashSet<Operation> delayedSet = delayedBy.get(c.id);
                                if (delayedSet == null) {
                                    delayedBy.put(c.id, new HashSet<>(Arrays.asList(sc)));
                                } else {
                                    delayedSet.add(sc);
                                }
                                delayed[c.id] = true;
                                pq.remove(c);
                                operations.add(c);
                            }
                        }
                    }
                }

            }
            Operation cur = pq.poll();
            if (cur != null) {
                // Next release time (when something more urgent could arrive)
                int nextRelease = (!operations.isEmpty() ? startMin[operations.peek().id] : Integer.MAX_VALUE);
                // Run the operation until either it finishes or a new job arrives
                int runTime = min(cur.p, nextRelease - t);
                cur.p -= runTime;
                t += runTime;
                if (cur.p != 0) {
                    if (!delayed[cur.id]) {
                        pq.offer(cur);
                    } else {
                        operations.add(cur);// preempted, reinsert
                    }
                } else {
                    for (Operation op : operations) {
                        if (delayed[op.id]) {
                            HashSet<Operation> delayedSet = delayedBy.get(op.id);
                            delayedSet.remove(cur);
                            if (delayedSet.isEmpty()) {
                                delayed[op.id] = false;
                                delayedBy.remove(op.id);
                                startMin[op.id] = t;
                                if (startMin[op.id] + duration[op.id] > endMax[op.id]) {
                                    return Outcome.INCONSISTENCY;
                                }
                                outcome = Outcome.CHANGE;
                            }
                        }
                    }
                }
            }
        }
        return outcome;
    }

    protected void update(int[] startMin, int[] duration, int[] endMax, int n) {
        this.n = n;
        for (int i = 0; i < n; i++) {
            this.startMin[i] = startMin[i];
            this.startMinNew[i] = startMin[i];
            this.startMax[i] = endMax[i] - duration[i];
            this.duration[i] = duration[i];
            this.endMin[i] = startMin[i] + duration[i];
            this.endMax[i] = endMax[i];
        }


        this.operations.clear();
        maxLct = 0;
        for (int i = 0; i < n; i++) {
            maxLct = Math.max(maxLct, endMax[i]);
        }
        for (int i = 0; i < n; i++) {
            K[i].p = duration[i];
            K[i].id = i;
            operations.add(K[i]);
        }
        this.pq.clear();
        Arrays.fill(this.delayed, 0, n, false);
        this.delayedBy.clear();
    }



    private boolean changed() {
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            changed |= startMinNew[i] > startMin[i];
            startMinNew[i] = startMin[i];
        }
        return false;
    }

    private boolean inconsistency() {
        for (int i = 0; i < n; i++) {
            if (startMin[i] > endMax[i]) return true;
        }
        return false;
    }

    private static class Operation {
        int id;
        int p;

        private Operation(int id, int p) {
            this.id = id;
            this.p = p;
        }

        @Override
        public String toString() {
            return "id : " + id + ", p : " + p;
        }
    }
}