/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw.scheduling.shipLoadling;

import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * Ship Loading Problem instance.
 *
 * @author Roger Kameugne
 */
public class ShipLoadingInstance {
    public int nbTasks;
    public int nbResources;
    public int resourceCapacity;
    public int[] sizes;
    public ArrayList<Integer>[] successors;
    public int horizon;
    public String name;
    int sumSizes;

    public ShipLoadingInstance (String fileName) throws Exception {
        Scanner s = new Scanner(new File(fileName)).useDelimiter("\\s+");
        while (!s.hasNextInt()) s.nextLine();
        nbTasks = s.nextInt();
        nbResources = s.nextInt();
        resourceCapacity = s.nextInt();
        sizes = new int[nbTasks];
        successors = new ArrayList[nbTasks];
        sumSizes = 0;
        for (int i = 0; i < nbTasks; i++) {
            successors[i] = new ArrayList<>();
            sizes[i] = s.nextInt();
            sumSizes += sizes[i];
            int nbSucc = s.nextInt();
            if (nbSucc > 0) {
                for (int j = 0; j < nbSucc; j++) {
                    int succ = s.nextInt();
                    successors[i].add(succ - 1);
                }
            }
        }
        name = fileName;
        horizon = sumSizes;
        s.close();
    }
}