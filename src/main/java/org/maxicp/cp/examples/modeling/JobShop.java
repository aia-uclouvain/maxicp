/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.engine.constraints.seqvar.Duration;
import org.maxicp.cp.engine.core.*;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.bool.BoolExpression;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.io.InputReader;

import java.util.*;
import java.util.function.Supplier;

import static org.maxicp.cp.CPFactory.makeSeqVar;
import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

public class JobShop {

    public static void main(String[] args) {
        InputReader inputReader = new InputReader("data/JOBSHOP/ft10.txt");//""data/JOBSHOP/jobshop-6-6-0");
        for (int i = 0 ; i < 4 ; i++)
            inputReader.skipLine(); // ignore first lines
        int nJobs = inputReader.getInt();
        int nMachines = inputReader.getInt();
        int[][] duration = new int[nJobs][nMachines];
        int[][] machine = new int[nJobs][nMachines];
        int sumDuration = 0;

        for (int i = 0; i < nJobs; i++) {
            for (int j = 0; j < nMachines; j++) {
                machine[i][j] = inputReader.getInt();
                duration[i][j] = inputReader.getInt();
                sumDuration += duration[i][j];
            }
        }

        ModelDispatcher model = makeModelDispatcher();
        IntervalVar[][] activities = new IntervalVar[nJobs][nMachines];
        ArrayList<IntervalVar>[] activitiesOnMachine = new ArrayList[nMachines];
        for (int i = 0 ; i < activitiesOnMachine.length ; i++)
            activitiesOnMachine[i] = new ArrayList<>();
        IntervalVar[] lastActivityOfJob = new IntervalVar[nJobs];

        for (int i = 0; i < nJobs; i++) {
            for (int j = 0; j < nMachines; j++) {
                // each activity has a fixed duration and is always present
                activities[i][j] = model.intervalVar(0, sumDuration, duration[i][j], true);
                int m = machine[i][j];
                activitiesOnMachine[m].add(activities[i][j]);
                // task comes before the other one on the same machine
                if (j > 0)
                    model.add(endBeforeStart(activities[i][j - 1], activities[i][j]));
            }
            lastActivityOfJob[i] = activities[i][nMachines - 1];
        }
        // collect the precedences between tasks
        for (int m = 0; m < nMachines ; m++) {
            // no task can overlap on a machine
            IntervalVar[] act = activitiesOnMachine[m].toArray(new IntervalVar[0]);
            model.add(noOverlap(act));
            // add the precedence
            for (int i = 0; i < act.length; i++) {
                for (int j = i + 1; j < act.length; j++) {
                    BoolExpression iBeforeJ = endBeforeStart(act[i], act[j]);
                    BoolExpression jBeforeI = endBeforeStart(act[j], act[i]);
                    // the tasks cannot overlap: either i << j, or j << i
                    model.add(neq(iBeforeJ, jBeforeI));
                }
            }
        }
        IntExpression makespan = max(Arrays.stream(lastActivityOfJob).map(task -> endOr(task, 0)).toArray(IntExpression[]::new));
        Objective minimizeMakespan = minimize(makespan);

        Supplier<Runnable[]> fixMakespan = () -> {
            if (makespan.isFixed())
                return EMPTY;
            return branch(() -> makespan.getModelProxy().add(eq(makespan,makespan.min())));
        };

        model.runCP((cp) -> {
            CPSeqVar[] machinesSeqvar = new CPSeqVar[nMachines];
            CPIntervalVar[][] allIntervals = new CPIntervalVar[nMachines][];
            for (int m = 0 ; m < nMachines ; m++) {
                CPIntervalVar[] intervalsOnMachine = activitiesOnMachine[m].stream().map(cp::getCPVar).toArray(CPIntervalVar[]::new);
                machinesSeqvar[m] = machineSeqVar(intervalsOnMachine);
                allIntervals[m] = intervalsOnMachine;
            }
            DFSearch search = cp.dfSearch(and(nodeSearch(machinesSeqvar, allIntervals), fixMakespan));
            long init = System.currentTimeMillis();
            search.onSolution(() -> {
                double elapsed = (double) (System.currentTimeMillis() - init) / 1000.0;
                System.out.printf("t=%.3f[s]: makespan: %s%n", elapsed, makespan);
            });
            SearchStatistics stats = search.optimize(minimizeMakespan); // actually solve the problem
            System.out.println("stats: \n" + stats);
        });

    }

    /**
     * Creates a sequence variable linked with interval variables.
     * The channeling happens through a constraint, hence the fixpoint must be triggered at every change.
     * @param intervals intervals that must be linked with a sequence variable
     * @return sequence variable linked with the intervals
     */
    public static CPSeqVar machineSeqVar(CPIntervalVar[] intervals) {
        int largestEnd = Arrays.stream(intervals).mapToInt(CPIntervalVar::endMax).max().getAsInt();
        CPSolver cp = intervals[0].getSolver();
        // start and end nodes are set as dummy intervals
        CPSeqVar seqVar = makeSeqVar(cp, intervals.length + 2, intervals.length, intervals.length + 1);
        CPIntervalVar dummyStart = new CPIntervalVarImpl(cp); // dummy start
        dummyStart.setPresent();
        dummyStart.setStart(0);
        dummyStart.setEnd(0);
        dummyStart.setLength(0);
        CPIntervalVar dummyEnd = new CPIntervalVarImpl(cp); // dummy end
        dummyEnd.setPresent();
        dummyEnd.setStart(largestEnd);
        dummyEnd.setEnd(largestEnd);
        dummyEnd.setLength(0);
        // all intervals for the channeling constraint
        CPIntervalVar[] intervalsWithDummy = new CPIntervalVar[intervals.length + 2];
        System.arraycopy(intervals, 0, intervalsWithDummy, 0, intervals.length);
        intervalsWithDummy[intervals.length] = dummyStart;
        intervalsWithDummy[intervals.length + 1] = dummyEnd;
        cp.post(new Duration(seqVar, intervalsWithDummy)); // post the channeling
        return seqVar;
    }

    /**
     * Binary branching that inserts the task with the best cost into the best location in its machine.
     * Best cost is defined as the number of insertions divided by the task duration.
     * The task is then inserted at its best location: the one with the largest number of successor
     * @param machines machines into which the tasks must be inserted
     * @param intervals intervals[m] = intervals related to machine m
     * @return binary branching
     */
    public static Supplier<Runnable[]> nodeSearch(CPSeqVar[] machines, CPIntervalVar[][] intervals) {
        int[] nodes = new int[Arrays.stream(machines).mapToInt(CPSeqVar::nNode).max().getAsInt()];
        return () -> {
            // choose the insertable task with the smallest number of insertions, across all machines
            // ties are broken by selecting the task with the largest duration
            //callback.run();
            //System.out.println("selection");
            int bestTask = -1;
            double bestCost = Double.MAX_VALUE;
            int bestMachine = -1;
            for (int m = 0 ; m < machines.length ; m++) {
                CPSeqVar machine = machines[m];
                int nInsertable = machine.fillNode(nodes, SeqStatus.INSERTABLE);
                for (int i = 0; i < nInsertable; i++) {
                    int task = nodes[i];
                    double cost = (double) machine.nInsert(task) / intervals[m][task].lengthMin();
                    if (cost < bestCost) {
                        bestTask = task;
                        bestMachine = m;
                        bestCost = cost;
                    }
                }
            }
            if (bestMachine == -1)
                return EMPTY;
            return insertAtBestOrRefute(machines[bestMachine], bestTask, nodes);
        };
    }

    /**
     * Generates two branches: either inserts the task after the node with the largest number of successor,
     * or refutes the insertion.
     * @param machine machine on which the insertion must happen
     * @param task task to insert
     * @param nodes array used for fill {@code machine.fillInsert(task, nodes)} operations
     * @return two branches
     */
    public static Runnable[] insertAtBestOrRefute(CPSeqVar machine, int task, int[] nodes) {
        int nInsert = machine.fillInsert(task, nodes);
        int bestNSucc = Integer.MIN_VALUE;
        int bestPred = 0;
        for (int i = 0 ; i < nInsert ; i++) {
            int pred = nodes[i];
            int nSucc = machine.nSucc(pred);
            if (nSucc > bestNSucc) {
                bestPred = pred;
                bestNSucc = nSucc;
            }
        }
        // either insert the task there or refute the location
        int finalBestPred = bestPred;
        int bestSucc = machine.memberAfter(bestPred);
        return branch(() -> machine.getModelProxy().add(insert(machine, finalBestPred, task)),
                () -> machine.getModelProxy().add(notBetween(machine, finalBestPred, task, bestSucc)));
    }

}
