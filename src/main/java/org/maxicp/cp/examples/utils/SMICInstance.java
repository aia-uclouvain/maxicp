/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * A Single Machine with Inventory Constraints (SMIC) instance.
 *
 * <p>A SMIC problem consists of scheduling a set J of n jobs split into two:
 * <ul>
 *   <li>the loading jobs J+ (type = 1, inventory delta &gt; 0)</li>
 *   <li>the unloading jobs J- (type = 0, inventory delta &lt; 0)</li>
 * </ul>
 * Each job has a release date, a processing time and an inventory modification delta.
 * An initial inventory and the capacity of the inventory storage are given.
 *
 * <p>Two file formats are supported:
 * <ul>
 *   <li><b>txt</b> — whitespace-separated, one job per line: {@code type processing weight release inventory}</li>
 *   <li><b>dzn</b> — MiniZinc data format with named keys</li>
 * </ul>
 */
public class SMICInstance {

    public final String name;
    public final int nbJob;
    public final int initInventory;
    public final int capaInventory;
    public final int[] type;
    public final int[] processing;
    public final int[] weight;
    public final int[] release;
    public final int[] inventory;
    public final int horizon;

    /**
     * Reads a SMIC instance from a file.
     *
     * @param filename path to the instance file (txt or dzn format)
     * @throws FileNotFoundException if the file does not exist
     */
    public SMICInstance(String filename) throws FileNotFoundException {
        this.name = filename;
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
            int sumProc = 0;
            int maxRel = Integer.MIN_VALUE;
            for (int i = 0; i < nbJob; i++) {
                type[i] = Integer.parseInt(t[i]);
                processing[i] = Integer.parseInt(p[i]);
                weight[i] = Integer.parseInt(w[i]);
                release[i] = Integer.parseInt(r[i]);
                inventory[i] = Integer.parseInt(in[i]);
                sumProc += processing[i];
                maxRel = Math.max(maxRel, release[i]);
            }
            horizon = maxRel + sumProc;
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