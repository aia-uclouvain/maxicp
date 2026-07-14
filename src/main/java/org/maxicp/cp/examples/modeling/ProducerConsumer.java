package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.examples.utils.ProducerConsumerInstance;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.FDSModeling;

import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

/**
 * RCPSP-CPR model (Modeling layer).
 */
public class ProducerConsumer {

    public static void main(String[] args) throws Exception {

        String filename = args.length > 0 ? args[0] : "data/PRODUCER_CONSUMER/ConsProd_bl2002.dzn";
        int timeLimit = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        ProducerConsumerInstance instance = new ProducerConsumerInstance(filename);
        solve(instance, timeLimit);
    }

    public static void solve(ProducerConsumerInstance data, int timeLimit) {

        // Setting data:
        int nTasks = data.numberOfTasks;
        int nRenewableResources = data.numberOfResourcesClassic;
        int nReservoirResources = data.numberOfResourcesConsProd;
        int[] capRenewableResource = data.capacitiesClassic;
        int[] capReservoirResource = data.capacitiesConsProd;
        int[] durations = data.processingTimes;
        int[][] consRenewableResource = data.heightsClassic;
        int[][] consReservoirConsumption = data.heightsCons;
        int[][] consReservoirProduction = data.heightsProd;
        int[][] precedences = data.precedences;
        int horizon = data.horizon();
        String name = data.name;

        // Model:
        ModelDispatcher model = makeModelDispatcher();

        IntervalVar[] intervals = new IntervalVar[nTasks];
        CumulFunction[] renewableResource = new CumulFunction[nRenewableResources];
        CumulFunction[] reservoirResource = new CumulFunction[nReservoirResources];
        for (int i = 0; i < nRenewableResources; i++) {
            renewableResource[i] = flat();
        }
        for (int i = 0; i < nReservoirResources; i++) {
            if (capReservoirResource[i] > 0) {
                reservoirResource[i] = step(model, 0, capReservoirResource[i]);
            } else {
                reservoirResource[i] = flat();
            }
        }

        IntExpression[] ends = new IntExpression[nTasks];
        for (int i = 0; i < nTasks; i++) {
            IntervalVar interval = model.intervalVar(0, horizon, durations[i], true);
            intervals[i] = interval;
            ends[i] = end(interval);
            for (int j = 0; j < nRenewableResources; j++) {
                if (consRenewableResource[j][i] > 0) {
                    renewableResource[j] = sum(renewableResource[j],
                            pulse(interval, consRenewableResource[j][i]));
                }
            }
            for (int j = 0; j < nReservoirResources; j++) {
                if (consReservoirConsumption[j][i] > 0) {
                    reservoirResource[j] = sum(reservoirResource[j],
                            stepAtStart(interval, consReservoirConsumption[j][i]));
                }
                if (consReservoirProduction[j][i] > 0) {
                    reservoirResource[j] = minus(reservoirResource[j],
                            stepAtEnd(interval, consReservoirProduction[j][i]));
                }
            }
        }

        for (int i = 0; i < nTasks; i++) {
            for (int j = 0; j < nTasks; j++) {
                if (precedences[i][j] == 1) {
                    model.add(endBeforeStart(intervals[i], intervals[j]));
                }
            }
        }

        for (int i = 0; i < nRenewableResources; i++) {
            model.add(le(renewableResource[i], capRenewableResource[i]));
        }

        for (int i = 0; i < nReservoirResources; i++) {
            model.add(alwaysIn(reservoirResource[i], 0, Integer.MAX_VALUE));
        }

        // Objective function
        IntExpression makespan = max(ends);
        Objective obj = minimize(makespan);

        model.runCP((cp) -> {
            DFSearch dfs = cp.dfSearch(fds(intervals));
            dfs.onSolution(() -> {
                System.out.println("Makespan: " + makespan.min());
            });

            long begin = System.currentTimeMillis();
            SearchStatistics stats = dfs.optimize(obj, statistics -> statistics.isCompleted()
                    || (System.currentTimeMillis() - begin) / 1000.0 > timeLimit);
            System.out.println(stats);
            System.out.println("time(s):" + (System.currentTimeMillis() - begin) / 1000.0);
        });
    }
}
