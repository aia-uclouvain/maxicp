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
    private int time = 0;
    private int n;
    private int numSCC =0;
    private List<List<Integer>> composantes = new ArrayList<>();
    private int[] SCCByNode;

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
                for(Integer el: tmp){
                    SCCByNode[el]= numSCC;
                }
                numSCC +=1;
            }
            inStack[stack.peek()] = false;
            stack.pop();
        }
    }

    public void findSCC(int[][] adjacencyMatrix) {
        composantes = new ArrayList<>();
        this.adjacencyList = new List[adjacencyMatrix.length];
        for (int i = 0; i < adjacencyMatrix.length; i++) {
            this.adjacencyList[i] = new ArrayList<>();
            for (int j = 0; j < adjacencyMatrix[i].length; j++) {
                if (adjacencyMatrix[i][j] >0) {
                    this.adjacencyList[i].add(j);
                }
            }
        }
//        this.adjacencyList = Arrays.copyOf(adjacencyList, adjacencyList.length);
        n = adjacencyList.length;

        dfs = new int[n];
        Low = new int[n];
        inStack = new boolean[n];
        stack=new Stack<>();
        SCCByNode=new int[n];

        Arrays.fill(SCCByNode, -1);

        n -= 1;

        for (int i = 0; i <= n; i++) {
            dfs[i] = -1;
            Low[i] = -1;
            inStack[i] = false;
        }

        for (int i = 0; i <= n; ++i) {
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
