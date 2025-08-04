package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.Arrays;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public class DistanceNew extends AbstractCPConstraint {

    private final CPSeqVar seqVar;
    private final int[] nodes;
    private final int[] inserts;
    private final int[][] dist;
    private final CPIntVar totalDist;

    private final int numNodes;
    private final int[][] preds;
    private final int[] numPreds;
    private final int[][] succs;
    private final int[] numSuccs;
    private final int[][] adjacencyMatrix;

    private final int[] minPred;
    private final int[] costMinPred;
    private int LBPredMin;
    private final int[] minDetour;
    private int LBDetourMin;
    private MinimumArborescence minimumArborescence;
    private int LBMinArborescence;
    private MinimumSpanningTree minimumSpanningTree;
    private final int[][] costForMST;
    private int LBMinSpanningTree;

    public DistanceNew(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar.getSolver());
        this.seqVar = seqVar;
        this.dist = dist;
        checkTriangularInequality(dist);
        this.totalDist = totalDist;
        this.nodes = new int[seqVar.nNode()];
        this.inserts = new int[seqVar.nNode()];

        this.numNodes = seqVar.nNode();
        this.preds = new int[numNodes][numNodes];
        this.numPreds = new int[numNodes];
        this.succs = new int[numNodes][numNodes];
        this.numSuccs = new int[numNodes];
        this.adjacencyMatrix = new int[numNodes][numNodes];

        this.minPred = new int[numNodes];
        this.costMinPred = new int[numNodes];
        this.minDetour = new int[numNodes];

        this.minimumArborescence = new MinimumArborescence(dist, seqVar.start());

        costForMST = new int[numNodes][numNodes];
        for (int i = 0; i < numNodes; i++) {
            for (int j = i; j < numNodes; j++) {
                costForMST[i][j] = Math.min(dist[i][j], dist[j][i]); // ensure symmetry
                costForMST[j][i] = costForMST[i][j]; // ensure symmetry
            }
        }
        this.minimumSpanningTree = new MinimumSpanningTree(numNodes, seqVar.start(), costForMST);

    }

    private static void checkTriangularInequality(int[][] dist) {
        for (int i = 0; i < dist.length; i++) {
            for (int j = 0; j < dist[i].length; j++) {
                int smallestDist = dist[i][j];
                for (int k = 0; k < dist.length; k++) {
                    int distWithDetour = dist[i][k] + dist[k][j];
                    if (distWithDetour < smallestDist) {
                        System.err.println("[WARNING]: triangular inequality not respected with distance matrix");
                        System.err.printf("[WARNING]: dist[%d][%d] + dist[%d][%d] < dist[%d][%d] (%d + %d < %d)%n", i, k, k, j, i, j,
                                dist[i][k], dist[k][j], dist[i][j]);
                        System.err.println("[WARNING]: this might remove some solutions");
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void post() {
        propagate();
        seqVar.propagateOnInsert(this);
        seqVar.propagateOnFix(this);
        totalDist.propagateOnBoundChange(this);
    }

    @Override
    public void propagate() {
        // update the current distance
        int nMember = seqVar.fillNode(nodes, MEMBER_ORDERED);
        int d = 0;
        for (int i = 0; i < nMember - 1; ++i) {
            d += dist[nodes[i]][nodes[i + 1]];
        }
        if (seqVar.isFixed()) {
            totalDist.fix(d);
            setActive(false);
            return;
        } else {
            // current distance is at least the current travel
            totalDist.removeBelow(d); //  10..200   5
            // take into account required nodes for the remaining distance
            initPredsAndSuccs();

            LBMinSpanningTree = updateLowerBoundMST();
//            LBMinArborescence = updateLowerBoundMinArborescence();
//            LBPredMin = updateLowerBoundPredMin();
//            LBDetourMin = updateLowerBoundDetourMin(d);

            updateUpperBound();

        }
        int maxDetour = totalDist.max() - d;
        // filter invalid insertions
        int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
        for (int i = 0; i < nInsertable; i++) {
            int node = nodes[i];
            int nPreds = seqVar.fillInsert(node, inserts);
            for (int p = 0; p < nPreds; p++) {
                int pred = inserts[p];
                filterEdge(pred, node, maxDetour);
            }
        }
    }

    private void initPredsAndSuccs() {
        for (int i = 0; i < numNodes; i++) { // from variables to values
            numSuccs[i] = seqVar.fillSucc(i, succs[i]);
            numPreds[i] = seqVar.fillPred(i, preds[i]);
        }
    }

    private int updateLowerBoundMST() {

        for (int i = 0; i < numNodes; i++) {
            Arrays.fill(adjacencyMatrix[i], 0);
        }

        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numSuccs[i]; j++) {
                adjacencyMatrix[i][succs[i][j]]=1;
                adjacencyMatrix[succs[i][j]][i]=1;
            }
        }

        minimumSpanningTree.primMST(adjacencyMatrix);

        totalDist.removeBelow(minimumSpanningTree.getCostMinimumSpanningTree());
        return minimumSpanningTree.getCostMinimumSpanningTree();
    }

    private int updateLowerBoundMinArborescence() {
        minimumArborescence.findMinimumArborescence(preds, numPreds);

        totalDist.removeBelow(minimumArborescence.getCostMinimumArborescence());
        return minimumArborescence.getCostMinimumArborescence();
    }


    /**
     * Updates the lower bound on the distance, based on the current sequence variable
     * This method computes the minimum detour for each node inserable
     *
     * @return
     */
    private int updateLowerBoundDetourMin(int currentDist) {
        int totalMinDetour = 0;

        Arrays.fill(minDetour, Integer.MAX_VALUE);

        int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
        for (int n = 0; n < nInsertable; n++) {
            int node = nodes[n];
            for (int p = 0; p < numPreds[node]; p++) {
                for (int s = 0; s < numSuccs[node]; s++) {
                    int pred = preds[node][p];
                    int succ = succs[node][s];
                    if (pred == succ) {
                        continue; // skip if pred and succ are the same
                    }

                    if (seqVar.hasEdge(pred, succ)) {
                        int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
                        if (detour < minDetour[node]) {
                            minDetour[node] = detour;
                        }
                    }
                }
            }
            if (minDetour[node] < Integer.MAX_VALUE) {
                totalMinDetour += minDetour[node];
            }
        }

        // remove the lower bound on the total distance
        totalDist.removeBelow(currentDist + totalMinDetour);
        return currentDist + totalMinDetour;
    }

    /**
     * Updates the lower bound on the distance, based on the current sequence variable
     * This method computes the sum of minimum distance from each node to its predecessors
     *
     * @return
     */
    private int updateLowerBoundPredMin() {
        int totalMinPred = 0;

        Arrays.fill(costMinPred, Integer.MAX_VALUE);

        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numPreds[i]; j++) {
                int pred = preds[i][j];
                if (dist[pred][i] < costMinPred[i]) {
                    costMinPred[i] = dist[pred][i];
                    minPred[i] = pred;
                }
            }
            if (costMinPred[i] < Integer.MAX_VALUE) {
                totalMinPred += costMinPred[i];
            }
        }

        // remove the lower bound on the total distance 
        totalDist.removeBelow(totalMinPred);
        return totalMinPred;
    }

    /**
     * Updates the upper bound on the distance, based on the current sequence variable
     */
    private void updateUpperBound() {
        // TODO 2
    }



    private void filterEdge(int pred, int node, int maxDetour) {
        if (seqVar.isNode(pred, MEMBER)) {
            int succ = seqVar.memberAfter(pred);
            int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];
            if (detour > maxDetour) { // detour is too long
                seqVar.notBetween(pred, node, succ);
                return;
            }
//            for (int i = 0; i < numSuccs[node]; i++) {
//                if (minPred[i] == node) {
//                    if (LBPredMin - costMinPred[node] - costMinPred[i] + detour > totalDist.max()) {
//                        seqVar.notBetween(pred, node, succ);
//                        return;
//                    }
//                }
//            }
//            else if (LBDetourMin - minDetour[node] + detour > totalDist.max()) {
//                seqVar.notBetween(pred, node, succ);
//            }
        }
    }




}