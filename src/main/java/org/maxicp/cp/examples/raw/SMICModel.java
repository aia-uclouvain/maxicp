package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.scheduling.CPCumulFunction;
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
 * A Single Machine with Inventory Constraints (SMIC) problem consists
 * of scheduling a set of J of n jobs split into in two:
 * - the loading jobs J+ and
 * - the unloading jobs J-.
 * Each job has a release date, a processing time and an inventory modification delta with
 * delta > 0 for loading job and delta < 0 for unloading job.
 * An initial inventory and the capacity of the inventory storage are given.
 * The aim is to sequence the jobs such that the makespan is minimized
 * while the inventory is range between 0 and the capacity of the inventory.
 */
public class SMICModel {

    public static void main(String[] args) throws Exception {
        String filename = "data/SMIC/data10_1.txt";
        if (args.length > 1) {
            filename = args[0];
        }
        int searchTime = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;
        SMICInstance instance = new SMICInstance(filename);
        SMICModel model = new SMICModel(instance, searchTime);
        System.out.println(instance.name + " | " + model.optimalSolution + " | " + model.elapsedTime + " | " + model.failures + " | " + model.status + " | " + "maxiCP_STK");
    }

    final SMICInstance data;
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
        cp.post(CPFactory.noOverlap(intervals));

        // Objective
        IntExpression makespan = max(ends);
        Objective obj = minimize(makespan);

        solution = new int[data.nbJob];
        // Search:
        DFSearch dfs = CPFactory.makeDfs(cp, Searches.setTimes(intervals));

        dfs.onSolution(() -> {
            System.out.println("solution found" + makespan.min());
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


    static class SMICInstance {
        public String name;
        public int nbJob;
        public int initInventory;
        public int capaInventory;
        public int[] type;
        public int[] processing;
        public int[] weight;
        public int[] release;
        public int[] inventory;
        public int horizon;

        public SMICInstance(String filename) throws FileNotFoundException {
            name = filename;
            Scanner s = new Scanner(new File(filename)).useDelimiter("\\s+");
            while (!s.hasNextLine()) {
                s.nextLine();
            }
            if (filename.contains("txt")) {
                int sumProc = 0;
                int maxRel = Integer.MIN_VALUE;
                nbJob = s.nextInt();
                initInventory = s.nextInt();
                capaInventory = s.nextInt();
                type = new int[nbJob];
                processing = new int[nbJob];
                weight = new int[nbJob];
                release = new int[nbJob];
                inventory = new int[nbJob];
                for (int i = 0; i < nbJob; i++) {
                    type[i] = s.nextInt();
                    processing[i] = s.nextInt();
                    weight[i] = s.nextInt();
                    release[i] = s.nextInt();
                    inventory[i] = s.nextInt();
                    sumProc += processing[i];
                    maxRel = Math.max(maxRel, release[i]);
                }
                horizon = maxRel + sumProc;

            } else {
                nbJob = Integer.parseInt(s.nextLine().split("\t=\t")[1].split(";")[0]);
                initInventory = Integer.parseInt(s.nextLine().split("\t=\t")[1].split(";")[0]);
                capaInventory = Integer.parseInt(s.nextLine().split("\t=\t")[1].split(";")[0]);
                type = new int[nbJob];
                processing = new int[nbJob];
                weight = new int[nbJob];
                release = new int[nbJob];
                inventory = new int[nbJob];
                String[] t = extractArrayValue(s.nextLine());
                String[] p = extractArrayValue(s.nextLine());
                String[] w = extractArrayValue(s.nextLine());
                String[] r = extractArrayValue(s.nextLine());
                String[] in = extractArrayValue(s.nextLine());
                for (int i = 0; i < nbJob; i++) {
                    type[i] = Integer.parseInt(t[i]);
                    processing[i] = Integer.parseInt(p[i]);
                    weight[i] = Integer.parseInt(w[i]);
                    release[i] = Integer.parseInt(r[i]);
                    inventory[i] = Integer.parseInt(in[i]);
                }
            }
            s.close();
        }

        private String[] extractArrayValue(String line) {
            String[] v = null;
            if (line.contains("=") && line.contains("[")) {
                int start = line.indexOf('[');
                int end = line.indexOf(']');
                if (start != -1 && end != -1 && end > start) {
                    String arrayStr = line.substring(start + 1, end);
                    v = arrayStr.split(", ");
                }
            }
            return v;
        }
    }
}
