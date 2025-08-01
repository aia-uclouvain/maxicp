package org.maxicp.cp.engine.constraints;

import java.util.Arrays;

public class SCC {

    private final int[][] adjacencyList;
    private final int[] numAdjByNode;
    private final int[] dfs;
    private final int[] low;
    private final boolean[] inStack;

    private int stackSize = 0;
    private final int[] stack;

    private int time;
    private int numSCC;
    private final int[] sccByNode;
    private int numNodes;

    /**
     * This class implements Tarjan's algorithm to find strongly connected components (SCCs) in a directed graph.
     * It uses depth-first search (DFS) to discover SCCs and assigns each node to its corresponding SCC.
     *
     * @param numNodes The number of nodes in the graph.
     */
    public SCC(int numNodes) {
        this.numNodes = numNodes;
        this.dfs = new int[numNodes];
        this.low = new int[numNodes];
        this.inStack = new boolean[numNodes];
        this.stack = new int[numNodes];
        this.sccByNode = new int[numNodes];
        this.adjacencyList = new int[numNodes][numNodes];
        this.numAdjByNode = new int[numNodes];
    }

    // stack methods

    private void pushStack(int u) {
        stack[stackSize++] = u;
    }

    private int popStack() {
        return stack[--stackSize];
    }

    private int peekStack() {
        return stack[stackSize - 1];
    }

    private void clearStack() {
        stackSize = 0;
    }

    private void dfs(int u) {
        dfs[u] = time;
        low[u] = time;
        time++;
        pushStack(u);
        inStack[u] = true;
        int[] adj = adjacencyList[u]; // list of edges from the node.

        if (adj == null) {
            return;
        }

        for (int i = 0; i < numAdjByNode[u]; i++) {
            int v = adj[i];
            if (dfs[v] == -1) { // v is not visited
                dfs(v);
                low[u] = Math.min(low[u], low[v]);
            }
            // differentiate back-edge and cross-edge
            else if (inStack[v]) // back-edge case
                low[u] = Math.min(low[u], dfs[v]);
        }

        if (low[u] == dfs[u]) {// u is head-node of SCC
            int numElInSCC = 0;
            while (peekStack() != u) {
                numAdjByNode[peekStack()] = 0;
                inStack[peekStack()] = false;
                sccByNode[peekStack()] = numSCC;
                popStack();
                numElInSCC++;
            }
            if (numElInSCC > 0) {
                sccByNode[peekStack()] = numSCC;
                numSCC += 1;
            }
            inStack[peekStack()] = false;
            popStack();
        }
    }

    public void findSCC(int[][] adjacencyMatrix) {
        for (int i = 0; i < adjacencyMatrix.length; i++) {
            numAdjByNode[i] = 0;
            for (int j = 0; j < adjacencyMatrix[i].length; j++) {
                if (adjacencyMatrix[i][j] > 0) {
                    adjacencyList[i][numAdjByNode[i]] = j;
                    numAdjByNode[i]++;
                }
            }
        }
        numNodes = adjacencyList.length;
        time = 0;
        numSCC = 0;
        Arrays.fill(sccByNode, -1);
        Arrays.fill(dfs, -1);
        Arrays.fill(low, -1);
        Arrays.fill(inStack, false);
        clearStack();

        numNodes -= 1;

        for (int i = 0; i <= numNodes; ++i) {
            if (dfs[i] == -1)
                dfs(i);   // call DFS for each undiscovered node.
        }
    }

    public int[] getSccByNode() {
        return sccByNode;
    }

    public int getNumSCC() {
        return numSCC;
    }
}