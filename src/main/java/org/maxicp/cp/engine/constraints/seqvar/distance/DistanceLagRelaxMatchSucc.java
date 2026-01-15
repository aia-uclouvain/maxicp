package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.maxicp.cp.engine.constraints.MinCostMaxFlow;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import static org.maxicp.modeling.algebra.sequence.SeqStatus.REQUIRED;

public class DistanceLagRelaxMatchSucc extends DistanceMatchingSuccessor {

    //for find cycle
    private List[] adjacencyList;
    private int[] dfs;
    private int[] low;
    private boolean[] dfsVisited;
    private Stack<Integer> stack=new Stack<>();
    private int time;
    private int n;
    private List<Integer> cycle=new ArrayList<>();
    private List<Integer> path=new ArrayList<>();
    private List<Integer> notInPath=new ArrayList<>();

    private int k=5;


    public DistanceLagRelaxMatchSucc(CPSeqVar seqVar, int[][] dist, CPIntVar totalDist) {
        super(seqVar, dist, totalDist);
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

        initResidualGraph();

        findCycle();

//        if (!findCompletPath()){
//            System.out.println("===============");
//            System.out.println(Arrays.deepToString(capMaxResidualGraph));
//            System.out.println(path);
//            System.out.println(notInPath);
//
//            for (int i=0; i< nNodes; i++){
//                if (notInPath.contains(i+1)){
//                    for (int j=0; j< nNodes; j++){
//                        if (minCostMaxFlow.getFlow()[i+1][nNodes + j +1]>0){
//                            costNetworkFlow[i+1][nNodes + j +1]+=k;
//                        }
//                    }
//                }
//            }
//
//            System.out.println("cost: "+minCostMaxFlow.getTotalCost());
//            minCostMaxFlow=new MinCostMaxFlow(numNodesInMatching);
//            minCostMaxFlow.run(totalDist.max(), 0, numNodesInMatching - 1, capMaxNetworkFlow, costNetworkFlow);
//            System.out.println("cost: "+minCostMaxFlow.getTotalCost());
//            System.out.println("real cost: "+(minCostMaxFlow.getTotalCost()-(notInPath.size()-1)*k));
//            System.out.println("real cost: "+(minCostMaxFlow.getTotalCost()-(notInPath.size()-2)*k));
//            System.out.println("real cost: "+(minCostMaxFlow.getTotalCost()-(notInPath.size()-5)*k));
//
//
//            totalDist.removeBelow(minCostMaxFlow.getTotalCost()-(notInPath.size()-1)*k);
//
//
//
//        }


//        if(findCycle()){
//            System.out.println("===============");
//            System.out.println("found cycle: "+cycle);
//            System.out.println("cost: "+minCostMaxFlow.getTotalCost());
//            System.out.println("cap: "+Arrays.deepToString(capMaxResidualGraph));
//            System.out.println("costNF: "+Arrays.deepToString(costNetworkFlow));
//            int [][] costNF=Arrays.copyOf(costNetworkFlow, costNetworkFlow.length);
//            for (int i=0; i< cycle.size(); i++){
//                int from=cycle.get(i);
//                int to=cycle.get((i+1)%cycle.size());
//                costNF[from][to]+=k;
//                costNF[to][from]+=k;
//            }
//            System.out.println("costNF: "+Arrays.deepToString(costNF));
//            System.out.println("flow: "+Arrays.deepToString(minCostMaxFlow.getFlow()));
//            minCostMaxFlow=new MinCostMaxFlow(numNodesInMatching);
//            minCostMaxFlow.run(totalDist.max(), 0, numNodesInMatching - 1, capMaxNetworkFlow, costNF);
//            for (int i = 0; i < numNodesInMatching; i++) {
//                Arrays.fill(capMaxResidualGraph[i], 0);
//                Arrays.fill(costResidualGraph[i], 0);
//            }
//            initResidualGraph = false;
//            initResidualGraph();
//            System.out.println("new cost");
//            System.out.println("cost: "+minCostMaxFlow.getTotalCost());
//            System.out.println("cap: "+Arrays.deepToString(capMaxResidualGraph));
//            System.out.println("flow: "+Arrays.deepToString(minCostMaxFlow.getFlow()));
//            System.out.println("real cost: "+(minCostMaxFlow.getTotalCost()-(cycle.size()/2-1)*k));
//            totalDist.removeBelow(minCostMaxFlow.getTotalCost()-(cycle.size()/2-1)*k);
//            System.out.println(findCycle());
//            System.out.println(cycle);
//        }
//        else {
//            System.out.println("no cycle found");
//        }

    }

    private boolean findCompletPath(){
        path.clear();
        notInPath.clear();
        for (int i=0; i<nNodes; i++){
            notInPath.add(i);
        }

        createAdjacencyList();

        int current=seqVar.end()+nNodes+1;
        notInPath.remove((Integer) seqVar.end());
        while ((current-nNodes-1)!=seqVar.start()) {
            int next= (int) adjacencyList[current].getFirst();
//            path.add(current);
            path.add(next);
            current=next+nNodes;
            notInPath.remove(notInPath.indexOf(next-1));
        }
        return notInPath.isEmpty();
    }

    private boolean findCycle(){
        cycle.clear();
        stack.clear();
        time=0;
        createAdjacencyList();
        n = adjacencyList.length;

        dfs = new int[n];
        low = new int[n];
        dfsVisited = new boolean[n];

        Arrays.fill(dfs, -1);
        Arrays.fill(low, -1);

        n -= 1;

        findSCC(adjacencyList);
//        if(DFS(numNodesInMatching-1)) {
//            cycle.removeFirst();
//            return true;
//        }
        return false;
    }

    private void createAdjacencyList() {
        adjacencyList=new ArrayList[numNodesInMatching];

        for (int i=0; i<numNodesInMatching; i++){
            adjacencyList[i]=new ArrayList();
            for (int j=0; j<numNodesInMatching; j++){
                if (capMaxResidualGraph[i][j]>0){
                    adjacencyList[i].add(j);
                }
            }
        }
    }

    private boolean dfs(int vertex, List<Integer> currentPath) {
        dfsVisited[vertex] = true;
        currentPath.add(vertex);

        for (int i = 0; i < adjacencyList[vertex].size(); i++) {
            int neighbor = (int) adjacencyList[vertex].get(i);
            if (!dfsVisited[neighbor] && dfs(neighbor, currentPath)) {
                return true;
            } else if (currentPath.contains(neighbor)) {
                return true;
            }
        }


        currentPath.remove((Object)vertex);
        return false;
    }

    private List<List<Integer>> composantes = new ArrayList<>();
    private int[] SCCByNode;
    private boolean[] inStack;
    private int numSCC =0;

    public void findSCC(List[] adjacencyList) {
        composantes = new ArrayList<>();
        this.adjacencyList = Arrays.copyOf(adjacencyList, adjacencyList.length);
        n = adjacencyList.length;

        dfs = new int[n];
        low = new int[n];
        inStack = new boolean[n];
        stack=new Stack<Integer>();
        SCCByNode=new int[n];

        Arrays.fill(SCCByNode, -1);

        n -= 1;

        for (int i = 0; i <= n; i++) {
            dfs[i] = -1;
            low[i] = -1;
            inStack[i] = false;
        }

        for (int i = 0; i <= n; ++i) {
            if (dfs[i] == -1)
                DFS(i);   // call DFS for each undiscovered node.
        }
    }

    private void DFS(int u) {
        dfs[u] = time;
        low[u] = time;
        time++;
        stack.push(u);
        inStack[u] = true;
        if(u<=nNodes){
            DFS(u+nNodes);
        }
        List<Integer> temp = adjacencyList[u]; // get the list of edges from the node.
        if (temp == null)
            return;

        for (int v : temp) {
            if (dfs[v] == -1) //If v is not visited
            {
                DFS(v);
                low[u] = Math.min(low[u], low[v]);
            }
//Differentiate back-edge and cross-edge
            else if (inStack[v]) //Back-edge case
                low[u] = Math.min(low[u], dfs[v]);
        }

        if (low[u] == dfs[u]) //If u is head node of SCC
        {
            List<Integer> tmp = new ArrayList<>();
            while (stack.peek() != u) {
                adjacencyList[stack.peek()]=new ArrayList();
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
}
