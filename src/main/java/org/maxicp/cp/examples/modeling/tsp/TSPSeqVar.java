package org.maxicp.cp.examples.modeling.tsp;

import org.maxicp.ModelDispatcher;
import org.maxicp.cp.examples.utils.TSPInstance;
import org.maxicp.cp.modeling.ConcreteCPModel;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.SeqVar;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;
import org.maxicp.util.Arrays;
import org.maxicp.util.algo.DistanceMatrix;

import java.util.List;

import static org.maxicp.modeling.Factory.*;

public class TSPSeqVar {

    public static void main(String[] args) {

        TSPInstance instance = new TSPInstance("data/TSP/gr21.xml");
        int n = instance.n;
        int[][] distanceMatrix = instance.distanceMatrix;

        // duplicate city 0 at position n, and set city n as the last one being visited
        int nNodes = n + 1;
        int start = 0;
        int end = n;
        // extend the distance matrix: end is a copy of start located at the end of the matrix
        int[][] distance = DistanceMatrix.extendMatrixAtEnd(distanceMatrix, List.of(start));
        int distanceUpperBound = n * Arrays.max(distance);

        // create a model of the problem
        ModelDispatcher model = Factory.makeModelDispatcher();
        // route performed by the salesman
        SeqVar route = model.seqVar(nNodes, start, end);
        // distance in the problem
        IntVar totalDistance = model.intVar(0, distanceUpperBound);

        // each city must be visited
        for (int city = 0 ; city < nNodes ; city++)
            model.add(require(route, city));
        // links the distance with the sequence through the distance matrix
        model.add(distance(route, distance, totalDistance));
        // objective consists in minimizing the total distance traveled
        Objective minimizeDistance = minimize(totalDistance);

        ConcreteCPModel cp = model.cpInstantiate(); // instantiate the previously symbolic model as a CP solver
        DFSearch search = cp.dfSearch(Searches.firstFailBinary(route));
        // print each solution found
        search.onSolution(() -> {
            System.out.println(route);
            System.out.println(totalDistance);
            System.out.println("------");
        });
        search.optimize(minimizeDistance); // actually solve the problem
    }

}
