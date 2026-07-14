package org.maxicp.cp.examples.utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class JobShopInstance {

    public int nJobs;
    public int nMachines;
    public int[][] duration;
    public int[][] machine;

    public JobShopInstance(String path) {
        try {
            FileInputStream istream = new FileInputStream(path);
            BufferedReader in = new BufferedReader(new InputStreamReader(istream));
            in.readLine();
            in.readLine();
            in.readLine();
            StringTokenizer tokenizer = new StringTokenizer(in.readLine());
            nJobs = Integer.parseInt(tokenizer.nextToken());
            nMachines = Integer.parseInt(tokenizer.nextToken());
            duration = new int[nJobs][nMachines];
            machine = new int[nJobs][nMachines];
            for (int i = 0; i < nJobs; i++) {
                tokenizer = new StringTokenizer(in.readLine());
                for (int j = 0; j < nMachines; j++) {
                    machine[i][j] = Integer.parseInt(tokenizer.nextToken());
                    duration[i][j] = Integer.parseInt(tokenizer.nextToken());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
}
