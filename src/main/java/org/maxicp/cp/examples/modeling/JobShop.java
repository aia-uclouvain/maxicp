package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.examples.utils.JobShopInstance;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.RankModeling;
import org.maxicp.search.SearchStatistics;

import java.util.ArrayList;
import java.util.Arrays;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

/**
 * The JobShop Problem.
 * <a href="https://en.wikipedia.org/wiki/Job_shop_scheduling">Wikipedia.</a>
 *
 * @author Pierre Schaus
 */
public class JobShop {

    public static IntervalVar[] flatten(IntervalVar[][] x) {
        return Arrays.stream(x).flatMap(Arrays::stream).toArray(IntervalVar[]::new);
    }

    public static void main(String[] args) {
        JobShopInstance instance = new JobShopInstance("data/JOBSHOP/ft10.txt");

        int nJobs = instance.nJobs;
        int nMachines = instance.nMachines;
        int[][] duration = instance.duration;
        int[][] machine = instance.machine;
        int sumDuration = 0;

        for (int i = 0; i < nJobs; i++) {
            for (int j = 0; j < nMachines; j++) {
                sumDuration += duration[i][j];
            }
        }

        ModelDispatcher model = makeModelDispatcher();
        IntervalVar[][] activities = new IntervalVar[nJobs][nMachines];

        for (int i = 0; i < nJobs; i++) {
            for (int j = 0; j < nMachines; j++) {
                activities[i][j] = model.intervalVar(0, sumDuration, duration[i][j], true);
            }
        }

        for (int j = 0; j < nJobs; j++) {
            for (int m = 1; m < nMachines; m++) {
                model.add(endBeforeStart(activities[j][m - 1], activities[j][m]));
            }
        }

        IntervalVar[][] toRank = new IntervalVar[nMachines][];
        for (int m = 0; m < nMachines; m++) {
            ArrayList<IntervalVar> machineActivities = new ArrayList<>();
            for (int j = 0; j < nJobs; j++) {
                for (int i = 0; i < nMachines; i++) {
                    if (machine[j][i] == m) {
                        machineActivities.add(activities[j][i]);
                    }
                }
            }
            IntervalVar[] onMachine = machineActivities.toArray(new IntervalVar[0]);
            model.add(noOverlap(onMachine));
            toRank[m] = onMachine;
        }

        IntervalVar[] lasts = Arrays.stream(activities)
                .map(job -> job[nMachines - 1])
                .toArray(IntervalVar[]::new);

        IntExpression makespan = max(Arrays.stream(lasts).map(task -> endOr(task, 0)).toArray(IntExpression[]::new));
        Objective minimizeMakespan = minimize(makespan);

        model.runCP((cp) -> {
            DFSearch search = cp.dfSearch(and(
                    new RankModeling(toRank),
                    () -> makespan.isFixed() ? EMPTY
                            : branch(() -> makespan.getModelProxy().add(eq(makespan, makespan.min())))));

            long init = System.currentTimeMillis();
            search.onSolution(() -> {
                double elapsed = (double) (System.currentTimeMillis() - init) / 1000.0;
                System.out.printf("t=%.3f[s]: makespan: %s%n", elapsed, makespan.min());
            });
            SearchStatistics stats = search.optimize(minimizeMakespan);
            System.out.println("stats: \n" + stats);
        });

    }
}
