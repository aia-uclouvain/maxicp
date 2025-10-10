/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 */

package org.maxicp.cp.engine.constraints.scheduling;


import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;

import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.maxicp.cp.CPFactory.makeIntervalVar;
import static org.maxicp.cp.CPFactory.makeSolver;

/**
 * Algorithm described in:
 * Brinkkötter, W., & Brucker, P. (2001).
 * Solving open benchmark instances for the job‐shop problem
 * by parallel head–tail adjustments.
 * Journal of Scheduling, 4(1), 53-64.
 *
 * Implemented by @Emma Legrand
 */
public class HeadTailConstraint extends AbstractCPConstraint {

    CPSolver solver;
    CPIntervalVar[] intervals;
    int nOps;
    Operation[] K;
    Operation[] Kreversed;
    PriorityQueue<Operation> operations;
    PriorityQueue<Operation> pq;
    boolean[] delayed;
    private boolean mirror;
    int maxLct;
    HashMap<Integer, HashSet<Operation>> delayedBy;

    public HeadTailConstraint(CPIntervalVar[] intervals, boolean mirror) {
        super(intervals[0].getSolver());
        // verify that all intervals are required
        for (CPIntervalVar interval : intervals) {
            if (!interval.isPresent()) {
                throw new IllegalArgumentException("All intervals must be required (not optional)");
            }
        }
        this.solver = intervals[0].getSolver();
        this.intervals = intervals;
        this.nOps = intervals.length;
        this.operations = new PriorityQueue<>(Comparator.comparingInt(a -> a.interval.startMin()));
        this.K = new Operation[nOps];
        this.Kreversed = new Operation[nOps];
        maxLct = 0;
        for (int i = 0; i < intervals.length; i++) {
            maxLct = Math.max(maxLct, intervals[i].endMax());
        }
        for (int i = 0; i < nOps; i++) {
            K[i] = new Operation(i, intervals[i], intervals[i].lengthMin());
            Kreversed[i] = new Operation(i, makeIntervalVar(solver, false, intervals[i].lengthMin()), intervals[i].lengthMin());
        }
        this.pq = new PriorityQueue<>(Comparator.comparingInt(a -> a.interval.endMax()));
        this.delayed = new boolean[nOps];
        this.delayedBy = new HashMap<>();
        this.mirror = mirror;
    }

    public HeadTailConstraint(CPIntervalVar[] intervals) {
        this(intervals, true);
    }

    @Override
    public void post() {
        for (int i = 0; i < nOps; i++) {
            intervals[i].propagateOnChange(this);
        }
        propagate();
    }

    @Override
    public void propagate() {
        adjustHead(this.K);
        if (mirror) {
            for (int i = 0; i < nOps; i++) {
                Kreversed[i].interval.setStartMin(maxLct - intervals[Kreversed[i].id].endMax());
                Kreversed[i].interval.setEndMax(maxLct - intervals[Kreversed[i].id].startMin());
            }
            adjustHead(this.Kreversed);
            for (int i = 0; i < intervals.length; i++) {
                if (this.Kreversed[i].interval.startMin() > maxLct - intervals[this.Kreversed[i].id].endMax()) {
                    intervals[this.Kreversed[i].id].setEndMax(maxLct - this.Kreversed[i].interval.startMin());
                }
            }
        }
    }

    private void adjustHead(Operation[] K) {   //Utiliser lct instead of ub and q     lct = ub-q
        operations.clear();
        pq.clear();
        delayedBy.clear();
        for (int i = 0; i < nOps; i++) {
            delayed[i] = false;
            operations.add(K[i]);
            K[i].p = K[i].interval.lengthMin();
        }
        Arrays.sort(K, Comparator.comparingInt(i -> ((Operation) i).interval.endMax()).reversed());
        int t = 0;
        while (!operations.isEmpty() || !pq.isEmpty()) {
            ArrayList<Operation> cs = new ArrayList<>();
            // Add all operations released up to current time
            while (!operations.isEmpty() && operations.peek().interval.startMin() <= t) {
                Operation op = operations.poll();
                if (delayed[op.id]) {
                    op.interval.setStartMin(op.interval.startMin() + 1);
                    operations.add(op);
                } else {
                    if (op.interval.startMin() == t) {
                        cs.add(op);
                    }
                    pq.offer(op);
                }
            }
            if (pq.isEmpty()) {
                // If nothing available, jump to next release time
                t = operations.peek().interval.startMin();
                continue;
            }

            for (Operation c : cs) {
                int pc = c.interval.lengthMin();
                int pplus = 0;
                ArrayList<Operation> Kc = new ArrayList<>();
                for (Operation op : K) {
                    if (op.id != c.id && op.p > 0) {
                        Kc.add(op);
                        pplus += op.p;
                    }
                }
                boolean alreadyDelayed = false;
                for (int i = 0; i < Kc.size(); i++) {
                    Operation sc = Kc.get(i);
                    if (sc.id != c.id) {
                        int psc = sc.interval.lengthMin();
                        if (!alreadyDelayed && c.interval.startMin() + pc + pplus > sc.interval.endMax()) {
                            c.interval.setStartMin(c.interval.startMin() + pplus);
                            alreadyDelayed = true;
                            delayedBy.put(c.id, new HashSet<>(Kc.subList(i, Kc.size())));
                            delayed[c.id] = true;
                            pq.remove(c);
                            operations.add(c);
                        } else if (!alreadyDelayed) {
                            pplus -= sc.p;
                            if (sc.p < psc && sc.interval.startMin() + psc > c.interval.startMin() && c.interval.startMin() + pc + psc > sc.interval.endMax()) {
                                c.interval.setStartMin(max(c.interval.startMin(), sc.interval.startMin() + psc));
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
                int nextRelease = (!operations.isEmpty() ? operations.peek().interval.startMin() : Integer.MAX_VALUE);

                // Run the operation until either it finishes or a new job arrives
                int runTime = min(cur.p, nextRelease - t);

                if (!delayed[cur.id]) {
                    cur.p -= runTime;
//                System.out.println("Set operation " + cur.id + " from t=" + t + " to t=" + (t + runTime));
                }
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
                                op.interval.setStartMin(t);
                            }
                        }
                    }
                }
            }
        }
    }

    private static class Operation {
        int id;
        CPIntervalVar interval;
        int p;

        private Operation(int id, CPIntervalVar interval, int p) {
            this.id = id;
            this.interval = interval;
            this.p = p;
        }

        @Override
        public String toString() {
            return "id : " + id + ", interval : " + interval + ", p : " + p;
        }
    }
}
