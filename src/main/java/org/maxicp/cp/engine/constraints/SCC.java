package org.maxicp.cp.engine.constraints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class SCC {
    private List[] adjacencyList;
    private int[] dfs;
    private int[] Low;
    private boolean[] inStack;
    private Stack<Integer> stack;
    private int time;
    private int numSCC;
    private List<List<Integer>> composantes;
    private int[] SCCByNode;
    private int numNodes;

    public SCC(int numNodes) {
        this.numNodes = numNodes;
        this.dfs = new int[numNodes];
        this.Low = new int[numNodes];
        this.inStack = new boolean[numNodes];
        this.stack = new Stack<>();
        this.SCCByNode = new int[numNodes];
        this.composantes = new ArrayList<>();
        this.adjacencyList = new List[numNodes];
        for (int i = 0; i < numNodes; i++) {
            this.adjacencyList[i] = new ArrayList<>();
        }
    }

    private void DFS(int u) {
        dfs[u] = time;
        Low[u] = time;
        time++;
        stack.push(u);
//        stack.pushBackNotFull(u);
        inStack[u] = true;
        List<Integer> temp = adjacencyList[u]; // get the list of edges from the node.

        if (temp == null)
            return;

        for (int v : temp) {
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
            List<Integer> tmp = new ArrayList<>();
            while (stack.peek() != u) {
//            while (stack.topBack() != u) {
                adjacencyList[stack.peek()] = new ArrayList();
                tmp.add(stack.peek());
                inStack[stack.peek()] = false;
//                SCCByNode[stack.topBack()]= numSCC;
                stack.pop();
            }
            tmp.add(stack.peek());
//            SCCByNode[stack.topBack()]= numSCC;
            if (tmp.size() > 1) {
                composantes.add(tmp);
                for (Integer el : tmp) {
                    SCCByNode[el] = numSCC;
                }
                numSCC += 1;
            }
            inStack[stack.peek()] = false;
            stack.pop();
        }
    }

    public void findSCC(int[][] adjacencyMatrix) {

        for (int i = 0; i < adjacencyMatrix.length; i++) {
            adjacencyList[i].clear();
            for (int j = 0; j < adjacencyMatrix[i].length; j++) {
                if (adjacencyMatrix[i][j] > 0) {
                    adjacencyList[i].add(j);
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
        composantes.clear();
        stack.clear();

        numNodes -= 1;


        for (int i = 0; i <= numNodes; ++i) {
            if (dfs[i] == -1)
                DFS(i);   // call DFS for each undiscovered node.
        }
    }

    public List<List<Integer>> getComposantes() {
        return composantes;
    }

    public int[] getSCCByNode() {
        return SCCByNode;
    }
}
