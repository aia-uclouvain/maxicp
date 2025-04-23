package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.LessOrEqual;
import org.maxicp.cp.engine.constraints.setvar.IsIncluded;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPSetVar;
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

/**
 * Max Independent Set Problem of a graph:
 * Given a graph, find the largest set of vertices such that no two vertices in the set are adjacent.
 * This problem is modeled with a set variable
 *
 * @author Amaury Guicchard and Pierre Schaus
 */
public class MaxIndependentSet {

    record Edge(int u, int v) {}
    record Instance(int nNodes, List<Edge> edges) {}


    public static void main(String[] args) {

        Instance instance;
        try {
            instance = readInstance("data/MIS/MIS-8-10");
        } catch (IOException e) {
            System.out.println("Error reading instance: " + e.getMessage());
            return;
        }

        CPSolver cp = makeSolver();
        CPSetVar set = makeSetVar(cp,instance.nNodes);

        CPBoolVar[] presence = makeBoolVarArray(instance.nNodes, i -> isIncluded(set,i));

        for (Edge e: instance.edges) {
            cp.post(le(sum(presence[e.u], presence[e.v]),1));
        }

        Objective obj = cp.maximize(set.card());

        DFSearch dfs = CPFactory.makeDfs(cp, firstFail(presence));

        dfs.onSolution(() -> {
            System.out.println("Solution found: " + set.card().min());
        });

        SearchStatistics stats = dfs.optimize(obj);
        System.out.format("Statistics: %s\n", stats);
    }


    public static Instance readInstance(String path) throws IOException {
        int nNodes = 0;
        List<Edge> edges = new java.util.ArrayList<>();
        FileInputStream fis = new FileInputStream(path);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("e")) {
                StringTokenizer st = new StringTokenizer(line);
                st.nextToken(); // skip "e"
                int u = Integer.parseInt(st.nextToken());
                int v = Integer.parseInt(st.nextToken());
                edges.add(new Edge(u, v));
                nNodes = Math.max(nNodes, Math.max(u, v));
            }
        }
        br.close();
        nNodes = nNodes + 1; // assuming nodes are 0-based
        return new Instance(nNodes, edges);

    }
}
