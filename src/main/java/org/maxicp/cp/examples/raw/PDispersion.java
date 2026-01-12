package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.AllDifferentDC;
import org.maxicp.cp.engine.constraints.AllDifferentFWC;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.Arrays;
import org.maxicp.util.io.InputReader;

import java.util.ArrayList;

public class PDispersion {

    public static void main(String[] args) {
        // MDG-b_40_n2000_m100_new1.txt
        // P-DISPERSION/MDG-a_1_100_m10_new1.txt
        // GKD_d_1_n500_coor_m50_div8_new1.txt
        InputReader reader = new InputReader("data/P-DISPERSION/GKD_d_1_n500_coor_m50_div8_new1.txt");


        int nLocations = reader.getInt();
        int nFacilities = reader.getInt();

        int[][] distance = new int[nLocations][nLocations];

        for (int i = 0; i < nLocations; i++) {
            for (int j = i+1; j < nLocations; j++) {
                int i_ = reader.getInt();
                int j_ = reader.getInt();
                assert(i == i_);
                assert(j == j_);
                distance[i][j] = (int) reader.getDouble();
                distance[j][i] = distance[i][j];
            }
        }

        int [][] minDistance = new int[nFacilities][nFacilities];
        for (int i = 0; i < nFacilities; i++) {
            for (int j = i+1; j < nFacilities; j++) {
                int i_ = reader.getInt();
                int j_ = reader.getInt();
                assert(i == i_);
                assert(j == j_);
                minDistance[i][j] = (int) reader.getDouble();
                minDistance[j][i] = minDistance[i][j];
            }
        }

        CPSolver cp = CPFactory.makeSolver();

        CPIntVar [] x = CPFactory.makeIntVarArray(cp, nFacilities, nLocations);

        //cp.post(new AllDifferentDC(x));
        cp.post(new AllDifferentFWC(x));


        CPIntVar[][] distances = new CPIntVar[nFacilities][nFacilities];
        ArrayList<CPIntVar> distancesFlat = new ArrayList<CPIntVar>();

        for (int i = 0; i < nFacilities; i++) {
            for (int j = i+1; j < nFacilities; j++) {
                distances[i][j] = CPFactory.element(distance,x[i],x[j]); // 250.000 * (50*50)
                distancesFlat.add(distances[i][j]);
            }
        }
        System.out.println("element constraints posted");
        CPIntVar objectiveVar = CPFactory.minimum(distancesFlat.toArray(new CPIntVar[0]));

        Objective obj = cp.maximize(objectiveVar);

        DFSearch dfs = CPFactory.makeDfs(cp, Searches.staticOrder(x));

        dfs.onSolution(() -> {;
            System.out.println("Objective: " + objectiveVar.min());
        });

        SearchStatistics stats = dfs.optimize(obj);

        System.out.println(stats);



    }
}
