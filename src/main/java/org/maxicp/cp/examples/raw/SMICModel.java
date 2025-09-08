package org.maxicp.cp.examples.raw;

import org.maxicp.Constants;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.scheduling.CPCumulFunction;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.cp.CPFactory.alwaysIn;
import static org.maxicp.cp.CPFactory.end;
import static org.maxicp.cp.CPFactory.le;
import static org.maxicp.cp.CPFactory.minus;
import static org.maxicp.cp.CPFactory.plus;
import static org.maxicp.cp.CPFactory.pulse;
import static org.maxicp.cp.CPFactory.stepAtStart;
import static org.maxicp.modeling.Factory.*;
import static org.maxicp.modeling.Factory.max;
import static org.maxicp.modeling.Factory.start;
/**
 * Model example for Single Machine Iventory Constraints (SMIC)
 *
 * A SMIC consists of scheduling n jobs J splited into loading J+ and unloading jobs J-.
 * Each job has a release date, a processing time and an inventory modification delta with
 * delta > 0 for loading job and delta < 0 for unloading job. An initial inventory and the
 * capacity of the inventory storage are given. The aim is to sequence the jobs such that the
 * makespan is minimized while the inventory is range between 0 and the capacity of the inventory.
 */
public class SMICModel {
    final org.maxicp.cp.examples.raw.SMICInstance data;
    //Best sol:
    public int optimalSolution;
    public int[] solution;
    public double elapsedTime;
    public boolean status;
    public int failures;
    double timeLimit;

    public SMICModel(SMICInstance data, double timeLimit) {
        this.data = data;
        this.timeLimit = timeLimit;
        // Initialized the model
        CPSolver cp = CPFactory.makeSolver();
        //Variables of the model
        CPIntervalVar[] intervals = new CPIntervalVar[data.nbJob];
        IntExpression[] starts = new IntExpression[data.nbJob];
        IntExpression[] ends = new IntExpression[data.nbJob];
        CPCumulFunction cumul = step(cp, 0, data.initInventory);

        for (int i = 0; i < data.nbJob; i++) {
            CPIntervalVar interval = makeIntervalVar(cp);
            interval.setStartMin(data.release[i]);
            //interval.setEndMax(data.horizon);
            interval.setLength(data.processing[i]);
            interval.setPresent();
            starts[i] = start(interval);
            ends[i] = end(interval);
            intervals[i] = interval;
            if (data.type[i] == 1) {
                cumul = plus(cumul, stepAtStart(intervals[i], data.inventory[i], data.inventory[i]));
            } else {
                cumul = minus(cumul, stepAtStart(intervals[i], data.inventory[i], data.inventory[i]));
            }
        }
        // constraints
        cp.post(alwaysIn(cumul, 0, data.capaInventory));
        cp.post(nonOverlap(intervals));

        // Objective
        IntExpression makespan = max(ends);
        Objective obj = minimize(makespan);

        solution = new int[data.nbJob];
        // Search:
        DFSearch dfs = CPFactory.makeDfs(cp, Searches.setTimes(intervals));

        dfs.onSolution(() -> {
            System.out.println("solution found"+makespan.min());
            optimalSolution = makespan.min();
            for (int i = 0; i < data.nbJob; i++) {
                solution[i] = intervals[i].startMin();
            }
        });
        //Launching search:
        long begin = System.currentTimeMillis();
        SearchStatistics stats = dfs.optimize(obj, statistics -> (System.currentTimeMillis() - begin) / 1000.0 > timeLimit);
        elapsedTime = (System.currentTimeMillis() - begin) / 1000.0;
        failures = stats.numberOfFailures();
        if (stats.isCompleted())
            status = true;
        else status = false;
    }

    public static void main(String[] args) throws Exception{
        if (args.length < 1) {
            throw new IllegalStateException("No instance provided in parameters");
        }
        final String filename = args[0];
        int searchTime = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;
        SMICInstance instance = new SMICInstance(filename);
        SMICModel model = new SMICModel(instance,  searchTime);
        System.out.println(instance.name + " | " + model.optimalSolution + " | " + model.elapsedTime + " | " + model.failures + " | " + model.status + " | " + "maxiCP_STK");
    }
}
