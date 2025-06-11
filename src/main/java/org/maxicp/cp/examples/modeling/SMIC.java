package org.maxicp.cp.examples.modeling;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.scheduling.CPCumulFunction;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.Factory.max;
import static org.maxicp.modeling.Factory.minimize;
import static org.maxicp.modeling.Factory.start;
import static org.maxicp.search.Searches.firstFail;

public class SMIC {
    public static void main(String[] args) throws Exception {
        SMICInstance data = new SMICInstance("data/SMIC/data10_3.txt");
//        System.out.println(data.nbJob);
//        System.out.println(data.initInventory);
//        System.out.println(data.capaInventory);
//        System.out.println(Arrays.toString(data.type));
//        System.out.println(Arrays.toString(data.processing));
//        System.out.println(Arrays.toString(data.weight));
//        System.out.println(Arrays.toString(data.release));
//        System.out.println(Arrays.toString(data.inventory));

//        ConvertToDzn conv = new ConvertToDzn("data/SMIC/data10_3.txt");
        SMICCPModel sample = new SMICCPModel(data);
        System.out.println(data.name + " | " + sample.bestMakespan + " | " + sample.elapsedTime + " | " + Arrays.toString(sample.bestSolution) + " | " + " MaxiCP ");
    }

    public static class SMICCPModel {
        final SMICInstance data;
        //Best sol:
        int bestMakespan = Integer.MAX_VALUE;
        int[] bestSolution;
        double elapsedTime;
        boolean status;
        int failures;
        public SMICCPModel (SMICInstance data) {
            this.data = data;
            // Initialized the model
            CPSolver cp = CPFactory.makeSolver();
            //Variables of the model
            CPIntervalVar[] intervals = new CPIntervalVar[data.nbJob];
            IntExpression[] starts = new IntExpression[data.nbJob];
            IntExpression[] ends = new IntExpression[data.nbJob];
            CPCumulFunction cumul = flat();
            if (data.initInventory > 0)
                cumul = step(cp, 0, data.initInventory);

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
            cp.post(nonOverlap(intervals));
            // Objective
            IntExpression makespan = max(ends);

            Objective obj = minimize(makespan);

            bestSolution = new int[data.nbJob];
            // Search:
            DFSearch dfs = new DFSearch(cp.getStateManager(), firstFail(starts));
            dfs.onSolution(() -> {
                bestMakespan = makespan.min();
                for (int i = 0; i < data.nbJob; i++) {
                    bestSolution[i] = intervals[i].startMin();
                }
            });
            //Launching search:
            long begin = System.currentTimeMillis();
            SearchStatistics stats = dfs.optimize(obj);
//            System.out.println(stats);
            elapsedTime = (System.currentTimeMillis() - begin)/1000.0;
            failures = stats.numberOfFailures();
        }
    }

    public static class SMICInstance {
        final String name;
        final int nbJob;
        final int initInventory;
        final int capaInventory;
        final int[] type;
        final int[] processing;
        final int[] weight;
        final int[] release;
        final int[] inventory;

        public SMICInstance (String filename) throws FileNotFoundException {
            name = filename;
            Scanner s = new Scanner(new File(filename)).useDelimiter("\\s+");
            while (!s.hasNextLine()) {s.nextLine();}
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
            }
            s.close();
        }
    }
    public static class ConvertToDzn {
        String filename;
        public ConvertToDzn(String filename) throws Exception{
            this.filename = filename;
            SMICInstance data = new SMICInstance(filename);
            String name = data.name.split("data/SMIC/")[1].split(".txt")[0] + ".dzn";
            try {
                File file = new File("data/SMIC/SMIC_DZN/" + name);
                if (file.createNewFile()) {
                    FileWriter writer = new FileWriter(file);
                    System.out.println(name);
                    writer.write("n_jobs\t=" + "\t"+ data.nbJob + ";\n");
                    writer.write("init_inventory\t=" + "\t" + data.initInventory + ";\n");
                    writer.write("capa_inventory\t=" + "\t" + data.capaInventory + ";\n");
                    writer.write("type_inventory\t=" + "\t" + Arrays.toString(data.type) + ";\n");
                    writer.write("processing\t=" + "\t" + Arrays.toString(data.processing) + ";\n");
                    writer.write("weight\t=" + "\t" + Arrays.toString(data.weight) + ";\n");
                    writer.write("release\t=" + "\t" + Arrays.toString(data.release) + ";\n");
                    writer.write("inventory\t=" + "\t" + Arrays.toString(data.inventory) + ";\n");
                    writer.close();
                }

            } catch (IOException e) {
                System.out.println("Error found during the creation of DZN file.");
                e.printStackTrace();
            }

        }
    }
}
