package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.engine.constraints.MinCostMaxFlow;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.util.exception.InconsistencyException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public class DistanceNew extends AbstractCPConstraint {
    private final int INF = Integer.MAX_VALUE;
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
    private final int[] nextMember;

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
    private MinimumSpanningTreeDetour minimumSpanningTreeDetour;
    private int LBMinSpanningTreeDetour;
    private MSTDetour mst;
    private int LBMatching;
    private MinCostMaxFlow minCostMaxFlow;
    private int numNodesInMatching;
    private int[][] capMaxNetworkFlow;
    private int[][] costNetworkFlow;
    private int[][] capMaxResidualGraph;
    private int[][] costResidualGraph;
    private int numEdgesResidualGraph;
    private int[][] edgesResidualGraph;

    //for find cycle
    private List[] adjacencyList;
    private int[] dfs;
    private int[] Low;
    private boolean[] inStack;
    private Stack<Integer> stack=new Stack<>();
    private int time = 0;
    private int n;
    private List<Integer> cycle=new ArrayList<>();

    private final boolean[] checkConsistency;

    private boolean useMSTDetour = false;
    private boolean useMST = false;
    private boolean useMinArborescence = false;
    private boolean usePredMin = false;
    private boolean useDetourMin = true;
    private boolean useMatching = false;


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
        this.nextMember = new int[numNodes];

        this.minPred = new int[numNodes];
        this.costMinPred = new int[numNodes];
        this.minDetour = new int[numNodes];

        this.minimumArborescence = new MinimumArborescence(dist, seqVar.start());
        this.numNodesInMatching = numNodes + numNodes + 2; // numNodes + numNodes + source + dest
        this.minCostMaxFlow = new MinCostMaxFlow(numNodesInMatching);
        this.capMaxNetworkFlow = new int[numNodesInMatching][numNodesInMatching];
        this.costNetworkFlow = new int[numNodesInMatching][numNodesInMatching];
        this.capMaxResidualGraph = new int[numNodesInMatching][numNodesInMatching];
        this.costResidualGraph = new int[numNodesInMatching][numNodesInMatching];
        this.edgesResidualGraph = new int[numNodesInMatching * numNodesInMatching][3];

        this.checkConsistency=new boolean[numNodes];

        costForMST = new int[numNodes][numNodes];
        for (int i = 0; i < numNodes; i++) {
            for (int j = i; j < numNodes; j++) {
                costForMST[i][j] = Math.min(dist[i][j], dist[j][i]); // ensure symmetry
                costForMST[j][i] = costForMST[i][j]; // ensure symmetry
            }
        }

        this.minimumSpanningTree = new MinimumSpanningTree(numNodes, seqVar.start(), costForMST);
        this.minimumSpanningTreeDetour = new MinimumSpanningTreeDetour(numNodes, seqVar.start(), costForMST);
        mst = new MSTDetour(seqVar.nNode());
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
        seqVar.propagateOnInsert(this);
        seqVar.propagateOnFix(this);
        totalDist.propagateOnBoundChange(this);
        propagate();
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
            cleanDataStructures();
            initPredsAndSuccs();

            if (useMSTDetour)
                LBMinSpanningTreeDetour = updateLowerBoundMSTD();
            if (useMST)
                LBMinSpanningTree = updateLowerBoundMST();
            if (useMinArborescence)
                LBMinArborescence = updateLowerBoundMinArborescence();
            if (usePredMin)
                LBPredMin = updateLowerBoundPredMin();
            if (useDetourMin)
                LBDetourMin = updateLowerBoundDetourMin(d);
            if (useMatching)
                LBMatching = updateLowerBoundMatching();

            updateUpperBound();

        }

        int maxDetour = totalDist.max() - d;
        // filter invalid insertions
        int nInsertable = seqVar.fillNode(nodes, INSERTABLE);
        for (int i = 0; i < nInsertable; i++) {
            int node = nodes[i];
//            int nPreds = seqVar.fillInsert(node, inserts);
            for (int p = 0; p < numPreds[node]; p++) {
                int pred = preds[node][p];
                filterEdge(pred, node, maxDetour);
            }
        }
    }

    private void initPredsAndSuccs() {
        for (int i = 0; i < numNodes; i++) { // from variables to values
            numSuccs[i] = seqVar.fillSucc(i, succs[i]);
            numPreds[i] = seqVar.fillPred(i, preds[i]);
            if (seqVar.isNode(i, MEMBER)) {
                nextMember[i] = seqVar.memberAfter(i);
            }
        }
    }

    private void cleanDataStructures() {
        for (int i = 0; useMatching && i < numNodesInMatching; i++) {
            Arrays.fill(capMaxNetworkFlow[i], 0);
            Arrays.fill(costNetworkFlow[i], 0);
            Arrays.fill(capMaxResidualGraph[i], 0);
            Arrays.fill(costResidualGraph[i], 0);
        }

        for (int i = 0; i < numNodes; i++) {
            Arrays.fill(adjacencyMatrix[i], 0);
        }

        Arrays.fill(minDetour, Integer.MAX_VALUE);
        Arrays.fill(costMinPred, Integer.MAX_VALUE);
        Arrays.fill(nextMember, -1);

    }

    private int updateLowerBoundMatching() {

        for (int i = 0; useMatching && i < numNodesInMatching; i++) {
            Arrays.fill(capMaxNetworkFlow[i], 0);
            Arrays.fill(costNetworkFlow[i], 0);
            Arrays.fill(capMaxResidualGraph[i], 0);
            Arrays.fill(costResidualGraph[i], 0);
        }

        for (int i = 0; i < numNodes; i++) {
            capMaxNetworkFlow[0][i + 1] = 1; // source to first layer
        }
        for (int i = 0; i < numNodes; i++) {
            capMaxNetworkFlow[numNodes + i + 1][numNodesInMatching - 1] = 1; // second layer to sink
        }
        int succ;
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numSuccs[i]; j++) {
                succ = succs[i][j];
                capMaxNetworkFlow[i + 1][numNodes + succ + 1] = 1; // first layer to second layer
                costNetworkFlow[i + 1][numNodes + succ + 1] = dist[i][succ];
            }
            if (!seqVar.isNode(i, REQUIRED)) {
                capMaxNetworkFlow[i + 1][numNodes + i + 1] = 1;
            }
        }

        minCostMaxFlow.run(totalDist.max(), 0, numNodesInMatching - 1, capMaxNetworkFlow, costNetworkFlow);

        totalDist.removeBelow(minCostMaxFlow.getTotalCost());

        return minCostMaxFlow.getTotalCost();
    }

    private int updateLowerBoundMSTD() {

//        int[] succInSeq = new int[numNodes];

        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numSuccs[i]; j++) {
                adjacencyMatrix[i][succs[i][j]] = 1;
                adjacencyMatrix[succs[i][j]][i] = 1;
            }
//            succInSeq[i] = seqVar.memberAfter(i);
        }

        int nMember = seqVar.fillNode(nodes, MEMBER_ORDERED);

        //minimumSpanningTreeDetour.primMST(adjacencyMatrix, succInSeq, nMember, nodes);
        //totalDist.removeBelow(minimumSpanningTreeDetour.getCostMinimumSpanningTree());
        int lowerBound = mst.compute(seqVar, dist);
        totalDist.removeBelow(lowerBound);


        return minimumSpanningTreeDetour.getCostMinimumSpanningTree();
    }

    private int updateLowerBoundMST() {


        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numSuccs[i]; j++) {
                adjacencyMatrix[i][succs[i][j]] = 1;
                adjacencyMatrix[succs[i][j]][i] = 1;
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


        int nInsertable = seqVar.fillNode(nodes, INSERTABLE_REQUIRED);
        for (int n = 0; n < nInsertable; n++) {
            int node = nodes[n];
            for (int p = 0; p < numPreds[node]; p++) {
                int pred = preds[node][p];
                if (!seqVar.isNode(pred, REQUIRED)) {
                    continue;
                }
                for (int s = 0; s < numSuccs[node]; s++) {
                    int succ = succs[node][s];
                    if (pred == succ || !seqVar.isNode(succ, REQUIRED)) {
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
        System.out.println(Arrays.toString(minDetour));
        System.out.println((currentDist + totalMinDetour));
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

        int nRequired = seqVar.fillNode(nodes, REQUIRED);
        for (int i = 0; i < nRequired; i++) {
            int node = nodes[i];
            for (int j = 0; j < numPreds[node]; j++) {
                int pred = preds[node][j];
                if (seqVar.isNode(pred, REQUIRED) && dist[pred][node] < costMinPred[node]) {
                    costMinPred[node] = dist[pred][node];
                    minPred[node] = pred;
                }
            }
            if (costMinPred[node] < INF) {
                totalMinPred += costMinPred[node];
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
        Arrays.fill(shortestPath, INF);
        shortestPath[src] = 0;
        int[] prev = new int[numNodesInMatching];
        Arrays.fill(prev, -1);
        int[] costPrev = new int[numNodesInMatching];
        Arrays.fill(costPrev, INF);
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
                    prev[v] = u;
                    costPrev[v] = wt;
                }
            }
        }

    }

    private void findCycle(){
        cycle.clear();
        stack.clear();
        createAdjacencyList();
        n = adjacencyList.length;

        dfs = new int[n];
        Low = new int[n];
        inStack = new boolean[n];


        n -= 1;

        DFS(0, 0);
    }

    private void createAdjacencyList() {
        adjacencyList=new ArrayList[numNodesInMatching];

        for (int i=0; i<=numNodesInMatching; i++){
            adjacencyList[i]=new ArrayList();
            for (int j=0; j<=numNodesInMatching; j++){
                if (capMaxResidualGraph[i][j]>0){
                    adjacencyList[i].add(j);
                }
            }
        }
    }

    private void DFS(int start, int u) {
        dfs[u] = time;
        Low[u] = time;
        time++;
        stack.push(u);
        inStack[u] = true;
        List<Integer> temp = adjacencyList[u]; // get the list of edges from the node.

        if (temp == null)
            return;

        for (int v : temp) {
            if (dfs[v] == -1) //If v is not visited
            {
                DFS(start, v);
                Low[u] = Math.min(Low[u], Low[v]);
            }
            else if (inStack[v]) //Back-edge case
                Low[u] = Math.min(Low[u], dfs[v]);
        }

        if (Low[u] == dfs[u]) {
            List<Integer> tmp = new ArrayList<>();
            while (stack.peek() != u) {
                adjacencyList[stack.peek()].clear();
                tmp.add(stack.peek());
                inStack[stack.peek()] = false;
                stack.pop();
            }
            tmp.add(stack.peek());
            if (tmp.size() > 1) {
                cycle=tmp;
                return;
            }
            inStack[stack.peek()] = false;
            stack.pop();
        }
    }


    private boolean checkOnlyOnePossiblePred(int node) {
        boolean onlyOnePossiblePred = true;
        int nPred;
        int nNode = numNodes + 1 + node;

        long[]SP=new long[numNodesInMatching];
        bellmanFord(numEdgesResidualGraph, edgesResidualGraph, nNode, SP);

        for (int pred = 0; pred < numNodes; pred++) {

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

        for (int succ = 0; succ < numNodes; succ++) {

            nSucc = succ + 1 + numNodes;

            if (node == succ || minCostMaxFlow.getFlow()[nNode][nSucc] > 0) {
                continue; // skip if already assigned
            }

            long[]SP=new long[numNodesInMatching];
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


    private void filterEdge(int pred, int node, int maxDetour) {
        if (seqVar.isNode(pred, MEMBER) && nextMember[pred] != -1) {
            int succ = nextMember[pred];

            int detour = dist[pred][node] + dist[node][succ] - dist[pred][succ];


            if (detour > maxDetour) { // detour is too long
                seqVar.notBetween(pred, node, succ);
                return;
            } else if (seqVar.isNode(node, REQUIRED)) {
                if (usePredMin && LBPredMin - costMinPred[node] - costMinPred[succ] + detour > totalDist.max()) {
                    seqVar.notBetween(pred, node, succ);
                    return;
                } else if (useDetourMin && LBDetourMin - minDetour[node] + detour > totalDist.max()) {
                    seqVar.notBetween(pred, node, succ);
                    return;
                }
            } else {
                if (usePredMin && LBPredMin - costMinPred[succ] + detour > totalDist.max()) {
                    seqVar.notBetween(pred, node, succ);
                    return;
                } else if (useDetourMin && LBDetourMin + detour > totalDist.max()) {
                    seqVar.notBetween(pred, node, succ);
                    return;
                }
            }
            if (useMatching) {
                if (checkConsistency[node]) {
                    return;
                }

                builResidualGraph(capMaxNetworkFlow, costNetworkFlow, minCostMaxFlow.getFlow(), capMaxResidualGraph, costResidualGraph);
                createListOfEdges();

                int predNode = minCostMaxFlow.getLinkedPred()[numNodes + 1 + node] - 1;
                int succNode = minCostMaxFlow.getLinkedSucc()[node + 1] - 1 - numNodes;

                if (seqVar.isNode(predNode, MEMBER) && checkOnlyOnePossiblePred(node)) {
                    seqVar.notBetween(seqVar.start(), node, predNode);
                }

                if (seqVar.isNode(succNode, MEMBER) && checkOnlyOnePossibleSucc(node)) {
                    seqVar.notBetween(succNode, node, seqVar.end());
                }

                checkConsistency[node] = true;
            }
        }

    }
}