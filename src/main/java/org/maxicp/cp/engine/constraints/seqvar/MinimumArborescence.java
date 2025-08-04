package org.maxicp.cp.engine.constraints.seqvar;

import javax.crypto.spec.PSource;
import java.util.Arrays;

public class MinimumArborescence {

    // https://www.cs.mcgill.ca/~lhamba/comp360/AlgorithmDesign.pdf
    // https://www.google.com/search?sca_esv=d590ad2ae697755e&rlz=1C5CHFA_enFR977FR977&udm=7&fbs=AIIjpHxU7SXXniUZfeShr2fp4giZ1Y6MJ25_tmWITc7uy4KIeoJTKjrFjVxydQWqI2NcOha3O1YqG67F0QIhAOFN_ob1aWGQOelbxvw0PKo40QtwvZMGAT8mh52EQduMaEwrkL-OLEnIgHQ7APoKxFV9hua55yCiA1pSqi8NqYaykPBkHQYt8sF3mLIH7UYTHYwhcJqGpMVh&q=minimum+cost+arborescence+algorithm&sa=X&ved=2ahUKEwjb_97l2NeOAxWJTKQEHbFgMAgQtKgLegQIDRAB&biw=1512&bih=823&dpr=2#fpstate=ive&vld=cid:9eb64a8c,vid:mZBcslesf-o,st:0

    private final int numNodes;
    private final int start;
    private final int[][] cost;
    private int[][] preds;
    private int[] numPreds;

    private final int[][] costZero;
    private final int[] costZeroPredMin;

    private final int[] predTree;
    private final boolean[] inTree;

    private int numCycle;
    private final int[] numCycleByNode;

    private final int[] inputMinByCycle;
    private final int[] costInputMinByCycle;

    private int costMinimumArborescence;

    public MinimumArborescence(int[][] cost, int start) {
        this.numNodes = cost.length;
        this.start = start;
        this.cost = cost;

        this.costZero = new int[numNodes][numNodes];
        this.costZeroPredMin = new int[numNodes];

        this.predTree = new int[numNodes];
        this.inTree = new boolean[numNodes];

        this.numCycle = 0;
        this.numCycleByNode = new int[numNodes];

        this.inputMinByCycle = new int[numNodes];
        this.costInputMinByCycle = new int[numNodes];

    }

    public void findMinimumArborescence(int[][] preds, int[] numPreds) {
        this.preds = preds;
        this.numPreds = numPreds;

        numCycle = 0;
        costMinimumArborescence = 0;
        Arrays.fill(costZeroPredMin, Integer.MAX_VALUE);
        Arrays.fill(numCycleByNode, -1);
        Arrays.fill(costInputMinByCycle, Integer.MAX_VALUE);
        Arrays.fill(inTree, false);

        buildCostZero();
        buildZeroTree();

        //si il n'y a pas de cycle alors on a trouver l'arborescence min
        if (isTree()) {
            return;
        }

        minInputByCycle();

    }


    private void buildCostZero() {
        //pour chaque sommet on fait cost(pred(u), u)-=costMin(pred(u), u)

        //search min pred for each node except the root

        for (int i = 0; i < numNodes; i++) {
            if (start == i) continue;
            for (int j = 0; j < numPreds[i]; j++) {
                int pred = preds[i][j];
                if (cost[pred][i] < costZeroPredMin[i]) {
                    costZeroPredMin[i] = cost[pred][i];
                }
            }
        }

        //modify initial cost by cost(pred(u), u)-=costPredMin(u)
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                if (start == j) continue;
                if (cost[i][j] > 0) {
                    costZero[i][j] = cost[i][j] - costZeroPredMin[j];
                }
            }
        }
    }


    private void buildZeroTree() {
        //faire le zero graphe
        for (int i = 0; i < numNodes; i++) {
            if (start == i) continue; // skip the start node
            for (int j = 0; j < numPreds[i]; j++) {
                int pred = preds[i][j];
                if (costZero[pred][i] == 0) {
                    predTree[i] = pred;
                    costMinimumArborescence += cost[pred][i];
                    break;
                }
            }
        }
    }

    private boolean isTree() {
        inTree[start] = true;

        for (int s = 0; s < numNodes; s++) {
            if (inTree[s] || numCycleByNode[s] != -1) continue;
            if (s == start) continue;

            int n = s;
            boolean[] tmp = new boolean[numNodes];
            do {
                tmp[n] = true;
                n = predTree[n];
            } while (n != -1 && !inTree[n] && numCycleByNode[n] == -1 && !tmp[n]);

            if (n == -1 || inTree[n]) {
                for (int i = 0; i < numNodes; i++) {
                    if (tmp[i]) {
                        inTree[i] = true;
                    }
                }
            }
            if (tmp[n]) {
                int m = n;
                do {
                    numCycleByNode[m] = numCycle;
                    m = predTree[m];
                } while (m != n);
                numCycle++;
            }
        }

        return numCycle == 0;
    }

    //on recupere l'arc entrant miniumm de chaque cycle
    private void minInputByCycle() {

        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numPreds[i]; j++) {
                int pred = preds[i][j];
                if (numCycleByNode[i] == -1) continue; // node not in a cycle
                if (numCycleByNode[i] == numCycleByNode[pred]) continue; // both in the same cycle
                if (costZero[pred][i] < costInputMinByCycle[numCycleByNode[i]]) { // attention il ne faut pas prendre l'arc deja dans le cycle
                    costInputMinByCycle[numCycleByNode[i]] = costZero[pred][i];
                    inputMinByCycle[numCycleByNode[i]] = pred;
                }
            }
        }

        for (int i = 0; i < numCycle; i++) {
            if (inputMinByCycle[i] != -1) {
                costMinimumArborescence += costInputMinByCycle[i];
            }
        }
    }

    public int getCostMinimumArborescence() {
        return costMinimumArborescence;
    }
}
