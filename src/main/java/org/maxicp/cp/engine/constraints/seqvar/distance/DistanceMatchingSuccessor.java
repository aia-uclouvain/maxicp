package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.constraints.IsEqualVar;
import org.maxicp.cp.engine.constraints.MinCostMaxFlow;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.MEMBER;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;

public class DistanceMatchingSuccessor extends AbstractDistance {

    protected final int[][] succs;
    protected final int[] numSuccs;
    protected MinCostMaxFlow minCostMaxFlow;
    protected final int numNodesInMatching;
    protected final int[][] capMaxNetworkFlow;
    protected final int[][] costNetworkFlow;
    protected boolean initResidualGraph;
    protected final int[][] capMaxResidualGraph;
    protected final int[][] costResidualGraph;
    private int numEdgesResidualGraph;
    private final int[][] edgesResidualGraph;

    private final boolean[] SPCompute;
    private final long[][] SP;

    protected final EdgeIterator edgeIterator;

    protected final boolean[] checkConsistency;

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
        this.SPCompute = new boolean[numNodesInMatching];
        this.SP = new long[numNodesInMatching][numNodesInMatching];
        edgeIterator = new SeqvarEdgeIterator(seqVar);
    }

    @Override
    public void updateLowerBound() {
        edgeIterator.update();
        initResidualGraph = false;
        Arrays.fill(checkConsistency, false);
        Arrays.fill(SPCompute, false);

        for (int i = 0; i < numNodesInMatching; i++) {
            Arrays.fill(capMaxResidualGraph[i], 0);
            Arrays.fill(costResidualGraph[i], 0);
            Arrays.fill(capMaxNetworkFlow[i], 0);
            Arrays.fill(costNetworkFlow[i], 0);
        }

        for (int i = 0; i < nNodes; i++) {
            numSuccs[i] = edgeIterator.fillSucc(i, succs[i]); // from variables to values
            capMaxNetworkFlow[0][i + 1] = 1; // source to first layer
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

    private boolean updateFlow() {
        for (int i = 1; i < nNodes; i++) {
            for (int j = nNodes + 1; j < numNodesInMatching - 1; j++) {
                if (minCostMaxFlow.getFlow()[i][j] > 0) {
                    if (!seqVar.hasEdge(i - 1, j - 1 - nNodes) && i - 1 != j - 1 - nNodes) {
                        return false;
                    }
                }
                if (!seqVar.hasEdge(i - 1, j - 1 - nNodes)) {
                    capMaxNetworkFlow[i][j] = 0;
                    costNetworkFlow[i][j] = 0;
                }
            }
        }
        return true;
    }

    @Override
    public void filterDetourForRequired(int pred, int node, int succ, int detour) {
        if (checkConsistency[node]) {
            return;
        }

        int predNode = minCostMaxFlow.getLinkedPred()[nNodes + 1 + node] - 1;
        int succNode = minCostMaxFlow.getLinkedSucc()[node + 1] - 1 - nNodes;

        initResidualGraph();

        if (seqVar.isNode(predNode, MEMBER) && checkOnlyOnePossiblePred(node)) {
            if (seqVar.start() != predNode && seqVar.start() != seqVar.memberBefore(predNode)) {
                int tmpPred = seqVar.memberBefore(predNode) == succNode ? (seqVar.memberBefore(succNode)==seqVar.start()?seqVar.start():seqVar.memberBefore(succNode)) : seqVar.memberBefore(predNode);
                seqVar.notBetween(seqVar.start(), node, tmpPred);
            }
        }


        if (seqVar.isNode(succNode, MEMBER) && checkOnlyOnePossibleSucc(node)) {
            if (seqVar.end() != succNode && seqVar.end() != seqVar.memberAfter(succNode)) {
                int tmpSucc = seqVar.memberAfter(succNode) == predNode ? (seqVar.memberAfter(predNode)==seqVar.end()?seqVar.end():seqVar.memberAfter(predNode)) : seqVar.memberAfter(succNode);
                seqVar.notBetween(tmpSucc, node, seqVar.end());
            }
        }


        checkConsistency[node] = true;
    }

    @Override
    public void filterDetourForOptional(int pred, int node, int succ, int detour) {
        if (checkConsistency[node]) {
            return;
        }

        int predNode = minCostMaxFlow.getLinkedPred()[nNodes + 1 + node] - 1;
        int succNode = minCostMaxFlow.getLinkedSucc()[node + 1] - 1 - nNodes;

        initResidualGraph();

        if (predNode == node && checkOnlyOnePossiblePred(node)) {
            seqVar.exclude(node);
        } else if (succNode == node && checkOnlyOnePossibleSucc(node)) {
            seqVar.exclude(node);
        }

        checkConsistency[node] = true;

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

        int INF = Integer.MAX_VALUE;
        Arrays.fill(shortestPath, INF);
        shortestPath[src] = 0;
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
                }
            }
        }

    }


    protected void initResidualGraph() {
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


        if (!SPCompute[nNode]) {
            bellmanFord(numEdgesResidualGraph, edgesResidualGraph, nNode, SP[nNode]);
            SPCompute[nNode] = true;
        }

        for (int pred = 0; pred < nNodes; pred++) {

            nPred = pred + 1;

            if (node == pred || minCostMaxFlow.getFlow()[nPred][nNode] > 0 || capMaxNetworkFlow[nPred][nNode] == 0) {
                continue; // skip if already assigned
            }

            // Check if the arc (nPred, nNode) is consistent with Régin 2002
            if (SP[nNode][nPred] != Integer.MAX_VALUE && SP[nNode][nPred] <= totalDist.max() - totalDist.min() - costResidualGraph[nPred][nNode]) {
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

        for (int succ = 0; succ < nNodes; succ++) {

            nSucc = succ + 1 + nNodes;

            if (node == succ || minCostMaxFlow.getFlow()[nNode][nSucc] > 0 || capMaxNetworkFlow[nNode][nSucc] == 0) {
                continue; // skip if already assigned
            }


            if (!SPCompute[nSucc]) {
                bellmanFord(numEdgesResidualGraph, edgesResidualGraph, nSucc, SP[nSucc]);
                SPCompute[nSucc] = true;
            }

            // Check if the arc (nPred, nNode) is consistent with Régin 2002
            if (SP[nSucc][nNode] != Integer.MAX_VALUE && SP[nSucc][nNode] <= totalDist.max() - totalDist.min() - costResidualGraph[nNode][nSucc]) {
                // Arc is consistent
                onlyOnePossibleSucc = false;
                break;
            }
        }

        return onlyOnePossibleSucc;
    }


}
