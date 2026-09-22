package org.maxicp.cp.examples.utils;

import java.io.File;
import java.util.Scanner;

public class ProducerConsumerInstance {
    public int[] capacitiesClassic;
    public int[] capacitiesConsProd;
    public int numberOfTasks;
    public int numberOfResourcesClassic;
    public int numberOfResourcesConsProd;
    public int heightsClassic[][];
    public int heightsCons[][];
    public int heightsProd[][];
    public int[] processingTimes;
    public int[][] precedences;
    int processingTimesSum;
    public String name;

    public ProducerConsumerInstance(String fileName) throws Exception {
        Scanner s = new Scanner(new File(fileName));

        numberOfResourcesClassic = Integer.parseInt(s.nextLine().split(" = ")[1].split(";")[0]);
        capacitiesClassic = new int[numberOfResourcesClassic];
        String[] cap_clas = s.nextLine().split("\\[ ")[1].split(" ];")[0].split(", ");
        for (int i = 0; i < numberOfResourcesClassic; i++) {
            capacitiesClassic[i] = Integer.parseInt(cap_clas[i]);
        }

        numberOfResourcesConsProd = Integer.parseInt(s.nextLine().split(" = ")[1].split(";")[0]);
        capacitiesConsProd = new int[numberOfResourcesConsProd];
        String[] cap_cons = s.nextLine().split("\\[ ")[1].split(" ];")[0].split(", ");
        for (int i = 0; i < numberOfResourcesConsProd; i++) {
            capacitiesConsProd[i] = Integer.parseInt(cap_cons[i]);
        }

        numberOfTasks = Integer.parseInt(s.nextLine().split(" = ")[1].split(";")[0]);
        processingTimes = new int[numberOfTasks];
        String[] dur = s.nextLine().split("\\[ ")[1].split(" ];")[0].split(", ");
        processingTimesSum = 0;
        for (int i = 0; i < numberOfTasks; i++) {
            processingTimes[i] = Integer.parseInt(dur[i]);
            processingTimesSum += processingTimes[i];
        }

        heightsClassic = new int[numberOfResourcesClassic][numberOfTasks];
        for (int j = 0; j < numberOfResourcesClassic; j++) {
            if (j < numberOfResourcesClassic - 1) {
                String[] cons = s.nextLine().split("\\| ")[1].split(", ");
                for (int i = 0; i < numberOfTasks; i++) {
                    heightsClassic[j][i] = Integer.parseInt(cons[i]);
                }
            } else {
                String[] cons = s.nextLine().split("\\| ")[1].split(" \\|")[0].split(", ");
                for (int i = 0; i < numberOfTasks; i++) {
                    heightsClassic[j][i] = Integer.parseInt(cons[i]);
                }
            }
        }

        heightsCons = new int[numberOfResourcesConsProd][numberOfTasks];
        for (int j = 0; j < numberOfResourcesConsProd; j++) {
            if (j < numberOfResourcesConsProd - 1) {
                String[] cons = s.nextLine().split("\\| ")[1].split(", ");
                for (int i = 0; i < numberOfTasks; i++) {
                    heightsCons[j][i] = Integer.parseInt(cons[i]);
                }
            } else {
                String[] cons = s.nextLine().split("\\| ")[1].split(" \\|")[0].split(", ");
                for (int i = 0; i < numberOfTasks; i++) {
                    heightsCons[j][i] = Integer.parseInt(cons[i]);
                }
            }
        }

        heightsProd = new int[numberOfResourcesConsProd][numberOfTasks];
        for (int j = 0; j < numberOfResourcesConsProd; j++) {
            if (j < numberOfResourcesConsProd - 1) {
                String[] cons = s.nextLine().split("\\| ")[1].split(", ");
                for (int i = 0; i < numberOfTasks; i++) {
                    heightsProd[j][i] = Integer.parseInt(cons[i]);
                }
            } else {
                String[] cons = s.nextLine().split("\\| ")[1].split(" \\|")[0].split(", ");
                for (int i = 0; i < numberOfTasks; i++) {
                    heightsProd[j][i] = Integer.parseInt(cons[i]);
                }
            }
        }

        precedences = new int[numberOfTasks][numberOfTasks];
        for (int i = 0; i < numberOfTasks; i++) {
            for (int j = 0; j < numberOfTasks; j++) {
                precedences[i][j] = -1;
            }
        }
        for (int i = 0; i < numberOfTasks; i++) {
            String line_preced = s.nextLine();
            if (!line_preced.contains("{  }")) {
                String preced_ = line_preced.split("\\{ ")[1].split("  },")[0];
                if (preced_ != "") {
                    String[] prd = preced_.split(", ");
                    for (int j = 0; j < prd.length; j++) {
                        int k = Integer.parseInt(prd[j]) - 1;
                        precedences[i][k] = 1;
                    }
                }
            }
        }

        name = fileName;
        s.close();
    }

    public int horizon() {
        return processingTimesSum;
    }
}
