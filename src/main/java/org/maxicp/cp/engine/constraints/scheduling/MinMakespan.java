package org.maxicp.cp.engine.constraints.scheduling;

import org.maxicp.Constants;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.state.StateInt;

import java.util.*;

import static java.lang.Math.max;
import static org.maxicp.cp.CPFactory.ge;

public class MinMakespan extends AbstractCPConstraint {

    PrecedenceGraph precedenceGraph;
    CPIntVar makespan;
    CPIntervalVar[] onMachine;
    Integer[] sortEst;
    public MinMakespan(PrecedenceGraph precedenceGraph, CPIntVar makespan, CPIntervalVar... activities){
        super(activities[0].getSolver());
        this.precedenceGraph = precedenceGraph;
        this.makespan = makespan;
        this.onMachine = activities;
        this.sortEst = new Integer[activities.length];
        for (int i = 0; i < activities.length; i++) {
            sortEst[i] = i;

        }
    }
    @Override
    public int priority() {
        return Constants.PIORITY_SLOW;
    }

    @Override
    public void post() {
        for (CPIntervalVar var : this.onMachine) {
            var.propagateOnChange(this);
        }
        propagate();
    }

    @Override
    public void propagate() {
        Arrays.sort(sortEst, Comparator.comparingInt(x ->onMachine[x].startMin()));
        int bound = 0;
        int[] p = new int[onMachine.length];
        for (int i = 0; i < onMachine.length; i++) {
            p[i] = onMachine[i].lengthMin();
        }

        PriorityQueue<Integer> pq = new PriorityQueue<>(Comparator.comparingInt(a-> precedenceGraph.getQ(onMachine[(int) a])).reversed());
        int t = 0;
        int idx = 0;
        while (idx < onMachine.length || !pq.isEmpty()) {
            // Add all operations released up to current time
            while (idx < onMachine.length && onMachine[sortEst[idx]].startMin() <= t) {
                pq.offer(sortEst[idx]);
                idx++;
            }

            if (pq.isEmpty()) {
                // If nothing available, jump to next release time
                t = onMachine[sortEst[idx]].startMin();
                continue;
            }

            int cur = pq.poll();

            // Next release time (when something more urgent could arrive)
            int nextRelease = (idx < onMachine.length ? onMachine[sortEst[idx]].startMin() : Integer.MAX_VALUE);

            // Run the operation until either it finishes or a new job arrives
            int runTime = Math.min(p[cur], nextRelease - t);

            p[cur] -= runTime;
            t += runTime;

            if (p[cur] == 0) {
                bound = max(bound,t+precedenceGraph.getQ(onMachine[cur]));

            } else {
                pq.offer(cur); // preempted, reinsert
            }
        }
        if (bound> makespan.min()){
            System.out.println(bound + ": " + makespan);
        }
        makespan.removeBelow(bound);

    }

}