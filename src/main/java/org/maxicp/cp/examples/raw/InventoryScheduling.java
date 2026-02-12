package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.scheduling.CPCumulFunction;
import org.maxicp.cp.engine.constraints.scheduling.CPFlatCumulFunction;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.Factory.*;


/**
 * The Inventory Scheduling Problem was introduced in the paper:
 * Minimizing makespan on a single machine with release dates and inventory constraints
 * Morteza Davari, Mohammad Ranjbar, Patrick De Causmaecker, Roel Leus
 * European Journal of Operational Research 2020
 *
 * @author Roger Kameugne, Pierre Schaus
 */
public class InventoryScheduling {

    public static void main(String[] args) throws FileNotFoundException {

        Instance data = new Instance("data/INVENTORY/data10_1.txt");
        // Initialized the model
        CPSolver cp = CPFactory.makeSolver();
        //Variables of the model
        CPIntervalVar[] intervals = new CPIntervalVar[data.nbJob];
        CPIntVar[] starts = new CPIntVar[data.nbJob];
        CPIntVar[] ends = new CPIntVar[data.nbJob];

        CPCumulFunction cumul = step(cp, 0, data.initInventory);

        for (int i = 0; i < data.nbJob; i++) {
            CPIntervalVar interval = makeIntervalVar(cp);
            interval.setStartMin(data.release[i]);
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
        // constraint
        cp.post(alwaysIn(cumul, 0, data.capaInventory));

        cp.post(CPFactory.noOverlap(intervals));
        CPCumulFunction cumulNoOverlap = new CPFlatCumulFunction();
        for (CPIntervalVar interval : intervals) {
            cumulNoOverlap = CPFactory.plus(cumulNoOverlap,CPFactory.pulse(interval, 1));
        }
        cp.post(alwaysIn(cumulNoOverlap,0, 1));

        // Objective
        IntExpression makespan = CPFactory.max(ends);
        Objective obj = minimize(makespan);

        DFSearch dfs = CPFactory.makeDfs(cp, Searches.staticOrderBinary(starts));

        dfs.onSolution(() -> {
            System.out.println("makespan: " + makespan.min());
        });
        SearchStatistics stats = dfs.optimize(obj);
        System.out.println(stats);

    }


    static class Instance {

        public String name;
        public int nbJob;
        public int initInventory;
        public int capaInventory;
        public int[] type;
        public int[] processing;
        public int[] release;
        public int[] inventory;

        public Instance(String filename) throws FileNotFoundException {
            name = filename;
            Scanner s = new Scanner(new File(filename)).useDelimiter("\\s+");
            while (!s.hasNextLine()) {
                s.nextLine();
            }
            nbJob = s.nextInt();
            initInventory = s.nextInt();
            capaInventory = s.nextInt();
            type = new int[nbJob];
            processing = new int[nbJob];
            release = new int[nbJob];
            inventory = new int[nbJob];
            for (int i = 0; i < nbJob; i++) {
                type[i] = s.nextInt();
                processing[i] = s.nextInt();
                s.nextInt(); // weight is not used
                release[i] = s.nextInt();
                inventory[i] = s.nextInt();
            }
            s.close();
        }
    }
}
