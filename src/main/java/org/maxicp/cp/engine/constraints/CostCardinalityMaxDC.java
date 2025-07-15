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
import java.util.List;

public class CostCardinalityMaxDC extends AbstractCPConstraint {

    private final CPIntVar[] x;
    private final int[] upper;
    private final int nValues;
    private final int nVars;
    private final int[][] costs;
    private final CPIntVar H; // Maximum cost allowed

    private final StateInt[] assignment;
    private int minCostAssignment;

    private int[][] capMaxResidualGraph;
    private int[][] costResidualGraph;

    /**
     * Constraint the maximum number of occurrences of a range of values in x.
     *
     * @param x     The variables to constraint (at least one)
     * @param upper The upper cardinality bounds,
     *              upper[i] is the maximum number of occurrences of value i in x
     * @param costs The costs associated with each value in x.
     */
    public CostCardinalityMaxDC(CPIntVar[] x, int upper[], int[][] costs, CPIntVar H) {
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
        }
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
        // TODO
        int[][] costNetworkFlow = new int[nValues + nVars + 2][nValues + nVars + 2];
        int[][] capMaxNetworkFlow = new int[nValues + nVars + 2][nValues + nVars + 2];
        buildNetworkFlow(costNetworkFlow, capMaxNetworkFlow);

        MinCostMaxFlow minCostMaxFlow = new MinCostMaxFlow(capMaxNetworkFlow, costNetworkFlow, H.max(), nVars);

        minCostMaxFlow.run(0, nVars + nValues + 1);

        minCostAssignment = minCostMaxFlow.getTotalCost();

        if (minCostAssignment > H.max() || minCostMaxFlow.getTotalFlow() != nVars) throw new InconsistencyException();
        H.removeBelow(minCostAssignment);

        // fill the assignment
        int[][] domains = new int[nVars][nValues];
        int[] numValuesByDomain = new int[nVars];
        for (int i = 0; i < nVars; i++) { // from variables to values
            numValuesByDomain[i] = x[i].fillArray(domains[i]);
            for (int j = 0; j < numValuesByDomain[i]; j++) {
                if (minCostMaxFlow.getFlow()[i + 1][nVars + 1 + domains[i][j]] > 0) {
                    assignment[i].setValue(domains[i][j]); // assign the value to the variable
                    break; // only one value can be assigned to a variable
                }
            }
        }

        //TODO: mettre graph en positif
        builResidualGraph(capMaxNetworkFlow, costNetworkFlow, H.max(), minCostMaxFlow.getFlow(), nVars + nValues + 2);


//        removeArcNotConsistent(numValuesByDomain, domains); // Régin 2002
        removeArcNotConsistentPivot(numValuesByDomain, domains); //Schmied 2024

    }

    private void removeArcNotConsistentPivot(int[] numValuesByDomain, int[][] domains) {

        int numNodes = capMaxResidualGraph.length;
        int[][] dist = new int[numNodes][];

        // Create a list of edges
        int[][] edges = new int[numNodes * numNodes][3];
        int[][] edgesReverse = new int[numNodes * numNodes][3];
        int edgeCount = 0;
        int costArcMax = 0;
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                if (capMaxResidualGraph[i][j] > 0) {
                    edges[edgeCount] = new int[]{i, j, costResidualGraph[i][j]};
                    edgesReverse[edgeCount] = new int[]{j, i, costResidualGraph[i][j]};
                    edgeCount++;
                }
                if (costArcMax < costResidualGraph[i][j]) {
                    costArcMax = costResidualGraph[i][j]; // Find the maximum cost of an arc
                }
            }
        }

        SCC scc = new SCC();
        scc.findSCC(capMaxResidualGraph);
        List<List<Integer>> sccs = scc.getComposantes();
        int[] sccByNode = scc.getSCCByNode();
        int[] pivots = selectPivotBySCC(sccs);
        int[] distMaxIn = new int[sccs.size()];
        int[] distMaxOut = new int[sccs.size()];

        // For each SCC, compute the shortest path from the pivot to all other nodes and from all nodes to the pivot
        for (int indexSCC = 0; indexSCC < pivots.length; indexSCC++) {
            int pivot = pivots[indexSCC];
            if (dist[pivot] == null) {
                dist[pivot] = bellmanFord(numNodes, edgeCount, edges, pivot); // Compute shortest path from pivot to all nodes
                int[] distPivotRev = bellmanFord(numNodes, edgeCount, edgesReverse, pivot); // Compute shortest path from all nodes to pivot
                for (int i = 0; i < numNodes; i++) {
                    if (dist[i] == null) {
                        dist[i] = new int[numNodes];
                        Arrays.fill(dist[i], Integer.MAX_VALUE);
                    }
                    if (distPivotRev[i] != 0) {
                        dist[i][pivot] = distPivotRev[i]; // Store the reverse distance
                    }
                    if (distMaxOut[indexSCC] < dist[pivot][i] && dist[pivot][i] != Integer.MAX_VALUE) {
                        distMaxOut[indexSCC] = dist[pivot][i]; // Maximum distance from pivot to any node in the SCC
                    }
                    if (distMaxIn[indexSCC] < dist[i][pivot] && dist[i][pivot] != Integer.MAX_VALUE) {
                        distMaxIn[indexSCC] = dist[i][pivot]; // Maximum distance from any node in the SCC to the pivot
                    }
                }
            }
        }


        for (int i = 0; i < nVars; i++) {
            int varNode = i + 1; // node representing variable i

            //iterate over all values of variable i
            for (int j = 0; j < numValuesByDomain[i]; j++) {
                int valueNode = nVars + 1 + domains[i][j]; // node representing value j of variable i
                int indexSCC = sccByNode[valueNode];

                if (assignment[i].value() == domains[i][j])
                    continue; // skip if already assigned

                if(sccByNode[varNode] != sccByNode[valueNode] || indexSCC == -1){
                    // there is no way to reach varNode from valueNode so:
                    // Arc is not consistent, remove it
                    x[i].remove(domains[i][j]);
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

                if (dist[valueNode] == null || dist[valueNode][valueNode] == Integer.MAX_VALUE) {
                    dist[valueNode] = bellmanFord(numNodes, edgeCount, edges, valueNode);
                }
                // Check if the arc (varNode, valueNode) is consistent with Régin 2002
                if (dist[valueNode][varNode] > H.max() - minCostAssignment - costResidualGraph[varNode][valueNode]) { //dR(f)(u,v)=dRs(f)(u,v)−dR(f)(s,u)+dR(f)(s,v)
                    // Arc is not consistent, remove it
                    x[i].remove(domains[i][j]);
                }
            }
        }

    }

    private void removeArcNotConsistent(int[] numValuesByDomain, int[][] domains) {

        int numNodes = capMaxResidualGraph.length;
        int[][] dist = new int[numNodes][];

        // Create a list of edges
        int[][] edges = new int[numNodes * numNodes][3];
        int edgeCount = 0;
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numNodes; j++) {
                if (capMaxResidualGraph[i][j] > 0) {
                    edges[edgeCount++] = new int[]{i, j, costResidualGraph[i][j]};
                }
            }
        }

        for (int i = 0; i < nVars; i++) {
            int varNode = i + 1; // node representing variable i

            //iterate over all values of variable i
            for (int j = 0; j < numValuesByDomain[i]; j++) {
                if (assignment[i].value() == domains[i][j]) continue; // skip if already assigned

                int valueNode = nVars + 1 + domains[i][j]; // node representing value j of variable i

                if (dist[valueNode] == null) {
                    dist[valueNode] = bellmanFord(numNodes, edgeCount, edges, valueNode);
                }

                // Check if the arc (varNode, valueNode) is consistent
                if (dist[valueNode][varNode] > H.max() - minCostAssignment - costResidualGraph[varNode][valueNode]) { //dR(f)(u,v)=dRs(f)(u,v)−dR(f)(s,u)+dR(f)(s,v)
                    // Arc is not consistent, remove it
                    System.out.println("Remove ("+varNode+","+valueNode+")");
                    x[i].remove(domains[i][j]);
                }
            }
        }

    }

    private int[] selectPivotBySCC(List<List<Integer>> composantes) {
        int[] pivots = new int[composantes.size()];
        for (int i = 0; i < composantes.size(); i++) {
            pivots[i] = selectPivot(composantes.get(i));
        }
        return pivots;
    }

    private int selectPivot(List<Integer> scc) {
        // Select the pivot as the node with the greatest degree
        int maxDegree = -1;
        int pivotNode = -1;
        for (Integer node : scc) {
            int degreeIn = 0;
            int degreeOut = 0;
            for (int i = 0; i < capMaxResidualGraph.length; i++) {
                if (capMaxResidualGraph[node][i] > 0) {
                    degreeOut++;
                }
                if (capMaxResidualGraph[i][node] > 0) {
                    degreeIn++;
                }
            }
            int tmp = (degreeIn + degreeOut) * Math.min(degreeIn, degreeOut);
            if (tmp > maxDegree) {
                maxDegree = tmp;
                pivotNode = node;
            }
        }
        return pivotNode;
    }

    private void builResidualGraph(int[][] capMaxNetworkFlow, int[][] costNetworkFlow, int H, int[][] flow, int numNodes) {
        capMaxResidualGraph = new int[numNodes][numNodes];
        costResidualGraph = new int[numNodes][numNodes];

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

        int[][] domains = new int[nVars][nValues];
        int[] numValues = new int[nVars];
        for (int i = 0; i < nVars; i++) { // from variables to values
            numValues[i] = x[i].fillArray(domains[i]);
            for (int j = 0; j < numValues[i]; j++) {
                capMaxNetworkFlow[i + 1][nVars + 1 + domains[i][j]] = 1; // capacity of 1 for each variable to value edge
                costNetworkFlow[i + 1][nVars + 1 + domains[i][j]] = costs[i][domains[i][j]]; // cost of assigning variable i to value j
            }
        }

        for (int i = 0; i < nValues; i++) { // from values to sink
            capMaxNetworkFlow[nVars + 1 + i][nVars + nValues + 1] = upper[i];
        }
    }

    private int[] bellmanFord(int numNodes, int edgeCount, int[][] edges, int src) {


        // Initially distance from source to all other vertices
        // is not known(Infinite).
        int[] dist = new int[numNodes];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[src] = 0;

        // Relaxation of all the edges V times, not (V - 1) as we
        // need one additional relaxation to detect negative cycle
        for (int i = 0; i < numNodes; i++) {
            for (int ne = 0; ne < edgeCount; ne++) {
                int u = edges[ne][0];
                int v = edges[ne][1];
                int wt = edges[ne][2];
                if (dist[u] != Integer.MAX_VALUE && dist[u] + wt < dist[v]) {

                    // If this is the Vth relaxation, then there is
                    // a negative cycle
                    if (i == numNodes - 1)
                        return new int[]{-1};

                    // Update shortest distance to node v
                    dist[v] = dist[u] + wt;
                }
            }
        }
        return dist;
    }

    public int getMinCostAssignment() {
        return minCostAssignment;
    }

    public StateInt[] getAssignment() {
        return assignment;
    }
}


