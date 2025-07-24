/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.engine.constraints;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.StateInt;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Implementation of the Global Cardinality Constraint with Costs from the paper
 * <p>
 * Margaux Schmied, Jean-Charles Régin:
 * Efficient Implementation of the Global Cardinality Constraint with Costs. CP 2024
 *
 * @author Margaux Schmied
 */
public class CostCardinalityMaxDC extends AbstractCPConstraint {

    private final int INF
            = Integer.MAX_VALUE;
    private final CPIntVar[] x;
    private final int[] upper;
    private final int nValues;
    private final int nVars;
    private final int[][] costs;
    private final CPIntVar H; // Maximum cost allowed

    private final StateInt[] assignment;
    private final MinCostMaxFlow minCostMaxFlow;
    private int minCostAssignment;

    private final int numNodes;
    private final int[][] costNetworkFlow;
    private final int[][] capMaxNetworkFlow;

    private final int[][] capMaxResidualGraph;
    private final int[][] costResidualGraph;

    private final int[][] edges;
    private final int[][] edgesReverse;
    private int edgeCount;
    private int costArcMax;

    private final int[][] domain;
    private final int[] domainSize;

    private final long[][] dist; // dist[i][j] is the shortest distance from node i to node j in the residual graph
    private final long[][] distReverse; // distReverse[i][j] is the shortest distance from node j to node i in the reverse residual graph

    private final SCC scc;
    private int[] sccByNode;
    private final int[] degreeMaxBySCC;
    private final int[] pivots;
    private final long[] distMaxIn;
    private final long[] distMaxOut;


    /**
     * Constraint the maximum number of occurrences of a range of values in x.
     *
     * @param x     The variables to constraint (at least one), only non-negative values
     * @param upper The upper cardinality bounds,
     *              upper[i] is the maximum number of occurrences of value i in x
     *              The size of upper must be equal to the number of columns in costs and equal to the larest value in x.
     * @param costs The costs associated with each value in x.
     */
    public CostCardinalityMaxDC(CPIntVar[] x, int[] upper, int[][] costs, CPIntVar H) {
        super(x[0].getSolver());
        nVars = x.length;
        this.x = CPFactory.makeIntVarArray(nVars, i -> x[i]);
        this.costs = costs;
        this.nValues = upper.length;
        this.upper = new int[upper.length];
        this.H = H;
        for (int i = 0; i < upper.length; i++) {
            if (upper[i] < 0) throw new IllegalArgumentException("upper bounds must be non negative" + upper[i]);
            this.upper[i] = upper[i];
        }
        // largest value in x
        int largest = Stream.of(x).mapToInt(CPIntVar::max).max().getAsInt();
        if (nValues < largest) {
            throw new IllegalArgumentException("upper bounds length must be at least as large as the largest value in x");
        }
        if (costs.length != x.length) {
            throw new IllegalArgumentException("costs must have the same length as upper bounds");
        }
        if (costs[0].length != nValues) {
            throw new IllegalArgumentException("costs must have the same number of columns as upper bounds");
        }
        assignment = new StateInt[nVars];
        for (int i = 0; i < nVars; i++) {
            assignment[i] = getSolver().getStateManager().makeStateInt(-1); // -1 means unassigned
            this.x[i] = x[i];
            if (x[i].min() < 0) {
                throw new IllegalArgumentException("variable domains must be non-negative");
            }
        }

        numNodes = nValues + nVars + 2; // source, variables, values, sink
        minCostMaxFlow = new MinCostMaxFlow(H.max(), numNodes);
        scc = new SCC(numNodes);

        costNetworkFlow = new int[numNodes][numNodes];
        capMaxNetworkFlow = new int[numNodes][numNodes];
        capMaxResidualGraph = new int[numNodes][numNodes];
        costResidualGraph = new int[numNodes][numNodes];

        edges = new int[numNodes * numNodes][3];
        edgesReverse = new int[numNodes * numNodes][3];

        // domain[i][0..domainSize[i]-1] contains the values of variable i
        domain = new int[nVars][nValues];
        domainSize = new int[nVars];

        dist = new long[numNodes][numNodes];
        distReverse = new long[numNodes][numNodes];

        degreeMaxBySCC = new int[numNodes];
        pivots = new int[numNodes];
        distMaxIn = new long[numNodes];
        distMaxOut = new long[numNodes];
    }

    @Override
    public void post() {
        for (CPIntVar var : x) {
            if (!var.isFixed())
                var.propagateOnDomainChange(this);
        }
        propagate();
    }


    @Override
    public void propagate() {

        cleanDataStructures();

        updateDomains();

        buildNetworkFlow(costNetworkFlow, capMaxNetworkFlow);

        minCostMaxFlow.run(0, numNodes-1, capMaxNetworkFlow, costNetworkFlow);

        minCostAssignment = minCostMaxFlow.getTotalCost();

        if (minCostMaxFlow.getTotalFlow() != nVars) throw InconsistencyException.INCONSISTENCY;
        H.removeBelow(minCostAssignment);

        // fill the assignment
        for (int i = 0; i < nVars; i++) { // from variables to values
            for (int j = 0; j < domainSize[i]; j++) {
                if (minCostMaxFlow.getFlow()[i + 1][nVars + 1 + domain[i][j]] > 0) {
                    assignment[i].setValue(domain[i][j]); // assign the value to the variable
                    break; // only one value can be assigned to a variable
                }
            }
        }

        builResidualGraph(capMaxNetworkFlow, costNetworkFlow, minCostMaxFlow.getFlow());

        removeArcNotConsistentPivot(); //Schmied 2024
//        removeArcNotConsistent(); // Régin 2002

    }

    private void updateDomains() {
        for (int i = 0; i < nVars; i++) { // from variables to values
            domainSize[i] = x[i].fillArray(domain[i]);
        }
    }

    private void cleanDataStructures() {
        edgeCount = 0;
        costArcMax = 0;
        for (int i = 0; i < numNodes; i++) {
            Arrays.fill(capMaxResidualGraph[i], 0);
            Arrays.fill(costResidualGraph[i], 0);
            Arrays.fill(dist[i], INF);
        }
    }

    private void removeArcNotConsistentPivot() {

        createListOfEdges();

        scc.findSCC(capMaxResidualGraph);
        int numSCC = scc.getNumSCC();
        sccByNode = scc.getSccByNode();

        selectPivotBySCC();

        // For each SCC, compute the shortest path from the pivot to all other nodes and from all nodes to the pivot
        for (int indexSCC = 0; indexSCC < numSCC; indexSCC++) {
            int pivot = pivots[indexSCC];
            bellmanFord(edgeCount, edges, pivot, dist[pivot]); // Compute shortest path from pivot to all nodes
            bellmanFord(edgeCount, edgesReverse, pivot, distReverse[pivot]); // Compute shortest path from all nodes to pivot
            for (int i = 0; i < numNodes; i++) {
                if (distReverse[pivot][i] != 0) {
                    dist[i][pivot] = distReverse[pivot][i]; // Store the reverse distance
                }
                if (distMaxOut[indexSCC] < dist[pivot][i] && dist[pivot][i] != INF) {
                    distMaxOut[indexSCC] = dist[pivot][i]; // Maximum distance from pivot to any node in the SCC
                }
                if (distMaxIn[indexSCC] < dist[i][pivot] && dist[i][pivot] != INF) {
                    distMaxIn[indexSCC] = dist[i][pivot]; // Maximum distance from any node in the SCC to the pivot
                }
            }
        }


        for (int i = 0; i < nVars; i++) {
            int varNode = i + 1; // node representing variable i
            //iterate over all values of variable i
            for (int j = 0; j < domainSize[i]; j++) {
                int valueNode = nVars + 1 + domain[i][j]; // node representing value j of variable i
                int indexSCC = sccByNode[valueNode];

                if (assignment[i].value() == domain[i][j])
                    continue; // skip if already assigned

                if (sccByNode[varNode] != sccByNode[valueNode] || indexSCC == -1) {
                    // there is no way to reach varNode from valueNode so:
                    // Arc is not consistent, remove it
                    x[i].remove(domain[i][j]);
                    continue;
                }
                // Check the SCC with the pivot
                if (distMaxIn[indexSCC] + distMaxOut[indexSCC] <= H.max() - minCostAssignment - costArcMax) {
                    // SCC is consistent, keep it
                    continue;
                }
                // Check arc with the pivot
                if (dist[valueNode][pivots[indexSCC]] + dist[pivots[indexSCC]][varNode] <= H.max() - minCostAssignment - costResidualGraph[varNode][valueNode]) {
                    // Arc is consistent, keep it
                    continue;
                }

                if (dist[valueNode][valueNode] == INF) { // If the distance is not computed yet, compute it
                    bellmanFord(edgeCount, edges, valueNode, dist[valueNode]);
                }
                // Check if the arc (varNode, valueNode) is consistent with Régin 2002
                if (dist[valueNode][varNode] > H.max() - minCostAssignment - costResidualGraph[varNode][valueNode]) {
                    // Arc is not consistent, remove it
                    x[i].remove(domain[i][j]);
                }
            }
        }

    }

    private void removeArcNotConsistent() {

        createListOfEdges();

        for (int i = 0; i < nVars; i++) {
            int varNode = i + 1; // node representing variable i
            //iterate over all values of variable i
            for (int j = 0; j < domainSize[i]; j++) {
                int valueNode = nVars + 1 + domain[i][j]; // node representing value j of variable i

                if (assignment[i].value() == domain[i][j])
                    continue; // skip if already assigned

                if (dist[valueNode][valueNode] == INF) { // If the distance is not computed yet, compute it
                    bellmanFord(edgeCount, edges, valueNode, dist[valueNode]);
                }
                // Check if the arc (varNode, valueNode) is consistent with Régin 2002
                if (dist[valueNode][varNode] > H.max() - minCostAssignment - costResidualGraph[varNode][valueNode]) {
                    // Arc is not consistent, remove it
                    x[i].remove(domain[i][j]);
                }
            }
        }

    }

    private void createListOfEdges() {
        // Create a list of edges
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                if (capMaxResidualGraph[i][j] > 0) {
                    edges[edgeCount][0] = i;
                    edges[edgeCount][1] = j;
                    edges[edgeCount][2] = costResidualGraph[i][j];

                    edgesReverse[edgeCount][0] = j;
                    edgesReverse[edgeCount][1] = i;
                    edgesReverse[edgeCount][2] = costResidualGraph[i][j];

                    edgeCount++;
                }
                if (costArcMax < costResidualGraph[i][j]) {
                    costArcMax = costResidualGraph[i][j]; // Find the maximum cost of an arc
                }
            }
        }
    }

    private void selectPivotBySCC() {
        int degreeIn;
        int degreeOut;
        int tmp;
        for (int node = 0; node < numNodes; node++) {
            if (sccByNode[node] == -1) {
                continue;
            }
            degreeIn = 0;
            degreeOut = 0;
            for (int k = 0; k < numNodes; k++) {  // search deg+(node) and deg-(node)
                if (capMaxResidualGraph[node][k] > 0) {
                    degreeOut++;
                }
                if (capMaxResidualGraph[k][node] > 0) {
                    degreeIn++;
                }
            }
            tmp = (degreeIn + degreeOut) * Math.min(degreeIn, degreeOut);
            if (tmp > degreeMaxBySCC[sccByNode[node]]) { // if node is a better pivot for his own SCC
                degreeMaxBySCC[sccByNode[node]] = tmp;
                pivots[sccByNode[node]] = node;
            }
        }
    }

    private void builResidualGraph(int[][] capMaxNetworkFlow, int[][] costNetworkFlow, int[][] flow) {
        // Build the residual graph from the flow and the original network flow capacities and costs
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                if (capMaxNetworkFlow[i][j] > 0 && flow[i][j] < capMaxNetworkFlow[i][j]) {
                    costResidualGraph[i][j] = costNetworkFlow[i][j];
                    capMaxResidualGraph[i][j] = capMaxNetworkFlow[i][j] - flow[i][j];
                }
                if (capMaxNetworkFlow[i][j] > 0 && flow[i][j] > 0) {
                    costResidualGraph[j][i] = -1 * costNetworkFlow[i][j];
                    capMaxResidualGraph[j][i] = flow[i][j];
                }
            }
        }
    }

    private void buildNetworkFlow(int[][] costNetworkFlow, int[][] capMaxNetworkFlow) {

        for (int i = 1; i <= nVars; i++) { // from source to variables
            capMaxNetworkFlow[0][i] = 1;
        }

        for (int i = 0; i < nVars; i++) { // from variables to values
            Arrays.fill(capMaxNetworkFlow[i + 1], 0);
            Arrays.fill(costNetworkFlow[i + 1], 0);
            for (int j = 0; j < domainSize[i]; j++) {
                capMaxNetworkFlow[i + 1][nVars + 1 + domain[i][j]] = 1; // capacity of 1 for each variable to value edge
                costNetworkFlow[i + 1][nVars + 1 + domain[i][j]] = costs[i][domain[i][j]]; // cost of assigning variable i to value j
            }
        }

        for (int i = 0; i < nValues; i++) { // from values to sink
            capMaxNetworkFlow[nVars + 1 + i][nVars + nValues + 1] = upper[i];
        }
    }

    /**
     * Shortest path distance from the src to all other nodes using Bellman-Ford algorithm.
     * Return null if a negative cycle is detected.
     */
    private void bellmanFord(int edgeCount, int[][] edges, int src, long[] dist) {
        // Initially distance from source to all other vertices
        // is not known(Infinite).
        Arrays.fill(dist, INF);
        dist[src] = 0;
        // Relaxation of all the edges V times, not (V - 1) as we
        // need one additional relaxation to detect negative cycle
        for (int i = 0; i < numNodes; i++) {
            for (int ne = 0; ne < edgeCount; ne++) {
                int u = edges[ne][0];
                int v = edges[ne][1];
                int wt = edges[ne][2];
                if (dist[u] != INF && dist[u] + wt < dist[v]) {
                    // V_th relaxation => negative cycle
                    if (i == numNodes - 1) {
                        throw InconsistencyException.INCONSISTENCY;
                    }
                    // Update shortest distance to node v
                    dist[v] = dist[u] + wt;
                }
            }
        }
    }

    public int getMinCostAssignment() {
        return minCostAssignment;
    }

    public StateInt[] getAssignment() {
        return assignment;
    }
}


