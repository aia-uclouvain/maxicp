package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.constraints.MinCostMaxFlow;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;

public class DistanceMatchingSuccessor extends AbstractDistance {

    private final int[][] succs;
    private final int[] numSuccs;
    private final MinCostMaxFlow minCostMaxFlow;
    private final int numNodesInMatching;
    private final int[][] capMaxNetworkFlow;
    private final int[][] costNetworkFlow;
    private boolean initResidualGraph;
    private final int[][] capMaxResidualGraph;
    private final int[][] costResidualGraph;
    private int numEdgesResidualGraph;
    private final int[][] edgesResidualGraph;

    private final long[] SP;

    private final EdgeIterator edgeIterator;

    private final boolean[] checkConsistency;

    public DistanceMatchingSuccessor(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
        this.succs = new int[nNodes][nNodes];
        this.numSuccs = new int[nNodes];
        this.numNodesInMatching = nNodes + nNodes + 2; // numNodes + numNodes + source + dest
        this.minCostMaxFlow = new MinCostMaxFlow(numNodesInMatching);
        this.capMaxNetworkFlow = new int[numNodesInMatching][numNodesInMatching];
        this.costNetworkFlow = new int[numNodesInMatching][numNodesInMatching];
        this.capMaxResidualGraph = new int[numNodesInMatching][numNodesInMatching];
        this.costResidualGraph = new int[numNodesInMatching][numNodesInMatching];
        this.edgesResidualGraph = new int[numNodesInMatching * numNodesInMatching][3];
        this.checkConsistency = new boolean[nNodes];
        this.SP = new long[numNodesInMatching];
        edgeIterator = new SeqvarEdgeIterator(seqVar);
    }

    @Override
    public void updateLowerBound() {
        edgeIterator.update();
        initResidualGraph = false;
        Arrays.fill(checkConsistency, false);
        for (int i = 0; i < numNodesInMatching; i++) {
            Arrays.fill(capMaxNetworkFlow[i], 0);
            Arrays.fill(costNetworkFlow[i], 0);
            Arrays.fill(capMaxResidualGraph[i], 0);
            Arrays.fill(costResidualGraph[i], 0);
        }
        for (int i = 0; i < nNodes; i++) { // from variables to values
            //numSuccs[i] = seqVar.fillSucc(i, succs[i]);
            numSuccs[i] = edgeIterator.fillSucc(i, succs[i]);
        }

        for (int i = 0; i < nNodes; i++) {
            capMaxNetworkFlow[0][i + 1] = 1; // source to first layer
        }
        for (int i = 0; i < nNodes; i++) {
            capMaxNetworkFlow[nNodes + i + 1][numNodesInMatching - 1] = 1; // second layer to sink
        }
        int succ;
        for (int i = 0; i < nNodes; i++) {
            for (int j = 0; j < numSuccs[i]; j++) {
                succ = succs[i][j];
                capMaxNetworkFlow[i + 1][nNodes + succ + 1] = 1; // first layer to second layer
                costNetworkFlow[i + 1][nNodes + succ + 1] = dist[i][succ];
            }
            if (!seqVar.isNode(i, REQUIRED)) {
                capMaxNetworkFlow[i + 1][nNodes + i + 1] = 1;
            }
        }

        minCostMaxFlow.run(totalDist.max(), 0, numNodesInMatching - 1, capMaxNetworkFlow, costNetworkFlow);

        totalDist.removeBelow(minCostMaxFlow.getTotalCost());
    }

    private void builResidualGraph(int[][] capMaxNF, int[][] costNF, int[][] flow, int[][] capMaxRG, int[][] costRG) {
        // Build the residual graph from the flow and the original network flow capacities and costs
        for (int i = 0; i < numNodesInMatching; i++) {
            for (int j = 0; j < numNodesInMatching; j++) {
                if (capMaxNF[i][j] > 0 && flow[i][j] < capMaxNF[i][j]) {
                    costRG[i][j] = costNF[i][j];
                    capMaxRG[i][j] = capMaxNF[i][j] - flow[i][j];
                }
                if (capMaxNF[i][j] > 0 && flow[i][j] > 0) {
                    costRG[j][i] = -1 * costNF[i][j];
                    capMaxRG[j][i] = flow[i][j];
                }
            }
        }

    }

    private void createListOfEdges() {
        numEdgesResidualGraph = 0;
        // Create a list of edges
        for (int i = 0; i < numNodesInMatching; i++) {
            for (int j = 0; j < numNodesInMatching; j++) {
                if (capMaxResidualGraph[i][j] > 0) {
                    edgesResidualGraph[numEdgesResidualGraph][0] = i;
                    edgesResidualGraph[numEdgesResidualGraph][1] = j;
                    edgesResidualGraph[numEdgesResidualGraph][2] = costResidualGraph[i][j];

                    numEdgesResidualGraph++;
                }
            }
        }
    }


    private void bellmanFord(int edgeCount, int[][] edges, int src, long[] shortestPath) {

        // Initially distance from source to all other vertices
        // is not known(Infinite).
        int INF = Integer.MAX_VALUE;
        Arrays.fill(shortestPath, INF);
        shortestPath[src] = 0;
//        int[] prev = new int[numNodesInMatching];
//        Arrays.fill(prev, -1);
//        int[] costPrev = new int[numNodesInMatching];
//        Arrays.fill(costPrev, INF);
        // Relaxation of all the edges V times, not (V - 1) as we
        // need one additional relaxation to detect negative cycle
        for (int i = 0; i < numNodesInMatching; i++) {
            for (int ne = 0; ne < edgeCount; ne++) {
                int u = edges[ne][0];
                int v = edges[ne][1];
                int wt = edges[ne][2];
                if (wt != INF && shortestPath[u] != INF && shortestPath[u] + wt < shortestPath[v]) {
                    // V_th relaxation => negative cycle
                    if (i == numNodesInMatching - 1) {
                        throw InconsistencyException.INCONSISTENCY;
                    }
                    // Update shortest distance to node v
                    shortestPath[v] = shortestPath[u] + wt;
//                    prev[v] = u;
//                    costPrev[v] = wt;
                }
            }
        }
//        System.out.println("prev " + src);
//        System.out.println(Arrays.toString(prev));
//        System.out.println(Arrays.toString(costPrev));

    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {
        if (checkConsistency[node]) {
            return;
        }

        int predNode = minCostMaxFlow.getLinkedPred()[nNodes + 1 + node] - 1;
        int succNode = minCostMaxFlow.getLinkedSucc()[node + 1] - 1 - nNodes;

        if (seqVar.isNode(predNode, MEMBER) && checkOnlyOnePossiblePred(node)) {
            seqVar.notBetween(seqVar.start(), node, predNode);
        }

        if (seqVar.isNode(succNode, MEMBER) && checkOnlyOnePossibleSucc(node)) {
            seqVar.notBetween(succNode, node, seqVar.end());
        }

        checkConsistency[node] = true;
    }

    private void initResidualGraph() {
        if (!initResidualGraph) {
            builResidualGraph(capMaxNetworkFlow, costNetworkFlow, minCostMaxFlow.getFlow(), capMaxResidualGraph, costResidualGraph);
            createListOfEdges();
            initResidualGraph = true;
        }
    }

    private boolean checkOnlyOnePossiblePred(int node) {
        boolean onlyOnePossiblePred = true;
        int nPred;
        int nNode = nNodes + 1 + node;

        initResidualGraph();
        bellmanFord(numEdgesResidualGraph, edgesResidualGraph, nNode, SP);

        for (int pred = 0; pred < nNodes; pred++) {

            nPred = pred + 1;

            if (node == pred || minCostMaxFlow.getFlow()[nPred][nNode] > 0) {
                continue; // skip if already assigned
            }

            // Check if the arc (nPred, nNode) is consistent with Régin 2002
            if (SP[nPred] != Integer.MAX_VALUE && SP[nPred] <= totalDist.max() - totalDist.min() - costResidualGraph[nPred][nNode]) {
                // Arc is consistent
                onlyOnePossiblePred = false;
                break;
            }
        }

        return onlyOnePossiblePred;
    }

    private boolean checkOnlyOnePossibleSucc(int node) {
        boolean onlyOnePossibleSucc = true;
        int nSucc;
        int nNode = node + 1;

        initResidualGraph();

        for (int succ = 0; succ < nNodes; succ++) {

            nSucc = succ + 1 + nNodes;

            if (node == succ || minCostMaxFlow.getFlow()[nNode][nSucc] > 0) {
                continue; // skip if already assigned
            }


            bellmanFord(numEdgesResidualGraph, edgesResidualGraph, nSucc, SP);

            // Check if the arc (nPred, nNode) is consistent with Régin 2002
            if (SP[nNode] != Integer.MAX_VALUE && SP[nNode] <= totalDist.max() - totalDist.min() - costResidualGraph[nNode][nSucc]) {
                // Arc is consistent
                onlyOnePossibleSucc = false;
                break;
            }
        }

        return onlyOnePossibleSucc;
    }


    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {
        if (checkConsistency[node]) {
            return;
        }

        int predNode = minCostMaxFlow.getLinkedPred()[nNodes + 1 + node] - 1;
        int succNode = minCostMaxFlow.getLinkedSucc()[node + 1] - 1 - nNodes;

        if (seqVar.isNode(predNode, MEMBER) && checkOnlyOnePossiblePred(node)) {
            seqVar.notBetween(seqVar.start(), node, predNode);
        }

        if (seqVar.isNode(succNode, MEMBER) && checkOnlyOnePossibleSucc(node)) {
            seqVar.notBetween(succNode, node, seqVar.end());
        }

        checkConsistency[node] = true;

    }
}
