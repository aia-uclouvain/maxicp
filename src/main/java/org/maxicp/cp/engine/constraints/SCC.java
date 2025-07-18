package org.maxicp.cp.engine.constraints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class SCC {
    private final int[][] adjacencyList;
    private final int[] numAdjByNode;
    private final int[] dfs;
    private final int[] Low;
    private final boolean[] inStack;
    private final Stack<Integer> stack;
    private int time;
    private int numSCC;
    private final int[] SCCByNode;
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
        this.Low = new int[numNodes];
        this.inStack = new boolean[numNodes];
        this.stack = new Stack<>();
        this.SCCByNode = new int[numNodes];
        this.adjacencyList = new int[numNodes][numNodes];
        this.numAdjByNode = new int[numNodes];
    }

    private void DFS(int u) {
        dfs[u] = time;
        Low[u] = time;
        time++;
        stack.push(u);
        inStack[u] = true;
        int[] adj = adjacencyList[u]; // get the list of edges from the node.

        if (adj==null) {
            return;
        }

        for (int i = 0; i < numAdjByNode[u]; i++) {
            int v= adj[i];
            if (dfs[v] == -1) //If v is not visited
            {
                DFS(v);
                Low[u] = Math.min(Low[u], Low[v]);
            }
            //Differentiate back-edge and cross-edge
            else if (inStack[v]) //Back-edge case
                Low[u] = Math.min(Low[u], dfs[v]);
        }

        if (Low[u] == dfs[u]) //If u is head node of SCC
        {
            int numElInSCC=0;
            while (stack.peek() != u) {
                numAdjByNode[stack.peek()] = 0;
                inStack[stack.peek()] = false;
                SCCByNode[stack.peek()] = numSCC;
                stack.pop();
                numElInSCC++;
            }
            if (numElInSCC > 0) {
                SCCByNode[stack.peek()] = numSCC;
                numSCC += 1;
            }
            inStack[stack.peek()] = false;
            stack.pop();
        }
    }

    public void findSCC(int[][] adjacencyMatrix) {

        for (int i = 0; i < adjacencyMatrix.length; i++) {
            numAdjByNode[i] = 0;
            for (int j = 0; j < adjacencyMatrix[i].length; j++) {
                if (adjacencyMatrix[i][j] > 0) {
                    adjacencyList[i][numAdjByNode[i]]=j;
                    numAdjByNode[i]++;
                }
            }
        }

        numNodes = adjacencyList.length;

        time = 0;
        numSCC = 0;
        Arrays.fill(SCCByNode, -1);
        Arrays.fill(dfs, -1);
        Arrays.fill(Low, -1);
        Arrays.fill(inStack, false);
        stack.clear();

        numNodes -= 1;


        for (int i = 0; i <= numNodes; ++i) {
            if (dfs[i] == -1)
                DFS(i);   // call DFS for each undiscovered node.
        }
    }

    public int[] getSCCByNode() {
        return SCCByNode;
    }

    public int getNumSCC() {
        return numSCC;
    }
}