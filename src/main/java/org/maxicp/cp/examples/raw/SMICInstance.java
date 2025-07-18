package org.maxicp.cp.examples.raw;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class SMICInstance {
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

    public SMICInstance (String filename) throws FileNotFoundException {
        name = filename;
        Scanner s = new Scanner(new File(filename)).useDelimiter("\\s+");
        while (!s.hasNextLine()) {s.nextLine();}
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
