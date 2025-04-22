package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.LessOrEqual;
import org.maxicp.cp.engine.constraints.setvar.IsIncluded;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPSetVarImpl;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;

import java.io.*;
import java.util.List;
import java.util.StringTokenizer;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.firstFail;

public class MaxIndependentSet {

    List<int[]> edges = new java.util.ArrayList<>();
    int nbNodes = -1;

    public MaxIndependentSet(String url) {
        readGraph(url);
    }

    public void readGraph(String url) {
        try {

            FileInputStream fis = new FileInputStream(url);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            String line;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("e")) {
                    StringTokenizer st = new StringTokenizer(line);
                    st.nextToken(); // skip "e"
                    int u = Integer.parseInt(st.nextToken());
                    int v = Integer.parseInt(st.nextToken());

                    edges.add(new int[]{u, v});

                    nbNodes = Math.max(nbNodes, Math.max(u, v));
                }
            }

            br.close();

            nbNodes = nbNodes + 1; // assuming nodes are 0-based


        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }


    }

    public void solve(){
        CPSolver cp = makeSolver();
        CPSetVarImpl set = new CPSetVarImpl(cp,nbNodes);
        CPBoolVar[] presence = makeBoolVarArray(cp, nbNodes);
        for (int i = 0; i < nbNodes; i++) {
            cp.post(new IsIncluded(presence[i], i, set));
        }
        for (int[] e: edges) {
            cp.post(new LessOrEqual(sum(presence[e[0]], presence[e[1]]), makeIntVar(cp,1,1)));
        }

        Objective obj = cp.maximize(set.card());

        DFSearch dfs = CPFactory.makeDfs(cp, firstFail(presence));

        dfs.onSolution(() -> {
            System.out.println("Solution found: " + set.card().min());
        });

        SearchStatistics stats = dfs.optimize(obj);
        System.out.format("Statistics: %s\n", stats);
    }


    public static void main(String[] args) {
        MaxIndependentSet mis = new MaxIndependentSet("data/MIS/MIS-8-10");
        mis.solve();
    }
}
