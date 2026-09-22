package org.maxicp.cp.examples.utils;

import org.maxicp.util.io.InputReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Flexible Jobshop Instances from
 * https://github.com/SchedulingLab/fjsp-instances/
 * Format:
 * First line: <number of jobs> <number of machines>
 * Then one line per job: <number of operations> and then, for each operation,
 * <number of machines for this operation> and for each machine, a pair
 * <machine> <processing time>.
 * Machine index starts at 0.
 */
public class FJSInstance {
    public int nJobs;
    public int nMachines;
    public int nTasks;
    public int[][] jobs; // jobs[j] = array of task IDs for job j
    public int[][] duration; // duration[t][m] = processing time of task t on machine m, or -1 if not
                             // possible

    public FJSInstance(String file) {
        InputReader reader = new InputReader(file);
        nJobs = reader.getInt();
        nMachines = reader.getInt();

        List<List<Integer>> jobsList = new ArrayList<>();
        List<int[]> durationList = new ArrayList<>();

        int taskId = 0;

        for (int i = 0; i < nJobs; i++) {
            int numOperations = reader.getInt();
            List<Integer> jobTasks = new ArrayList<>();
            for (int o = 0; o < numOperations; o++) {
                int numMachines = reader.getInt();
                int[] taskDuration = new int[nMachines];
                Arrays.fill(taskDuration, -1);
                for (int m = 0; m < numMachines; m++) {
                    int machineId = reader.getInt();
                    int time = reader.getInt();
                    taskDuration[machineId] = time;
                }
                jobTasks.add(taskId);
                durationList.add(taskDuration);
                taskId++;
            }
            jobsList.add(jobTasks);
        }

        nTasks = taskId;
        jobs = new int[nJobs][];
        for (int i = 0; i < nJobs; i++) {
            jobs[i] = jobsList.get(i).stream().mapToInt(Integer::intValue).toArray();
        }

        duration = new int[nTasks][];
        for (int i = 0; i < nTasks; i++) {
            duration[i] = durationList.get(i);
        }
    }
}
