/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.examples.modeling;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.search.SearchMethod;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.TimeIt;
import org.maxicp.util.io.InputReader;
import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

import java.util.Arrays;


/**
 * Stable Marriage problem:
 * Given n women and n men, where each woman (resp. man) has
 * ranked each man (resp. woman) with a unique number between 1 and n
 * in order of preference (the lower the number, the higher the preference),
 * say for summer internships, match the women and men such that
 * there is no pair of a woman and a man who would both prefer to be
 * matched with each other than with their actually matched ones.
 * If there are no such pairs, then the matching is said to be stable.
 * <a href="https://en.wikipedia.org/wiki/Stable_marriage_problem">Wikipedia</a>.
 */
public class StableMarriage {

    public static void main(String[] args) {

        InputReader reader = new InputReader("data/stable_marriage.txt");
        int n = reader.getInt();
        int[][] rankMen = reader.getIntMatrix(n, n);
        int[][] rankWomen = reader.getIntMatrix(n, n);

        // there are six solutions so this instance:
        /*
        man: 5,3,8,7,2,6,0,4,1
        woman: 6,8,4,1,7,0,5,3,2

        man: 5,4,8,7,2,6,0,3,1
        woman: 6,8,4,7,1,0,5,3,2

        man: 5,0,3,7,4,8,2,1,6
        woman: 1,7,6,2,4,0,8,3,5

        man: 5,0,3,7,4,6,2,1,8
        woman: 1,7,6,2,4,0,5,3,8

        man: 5,3,0,7,4,6,2,1,8
        woman: 2,7,6,1,4,0,5,3,8

        man: 6,4,8,7,2,5,0,3,1
        woman: 6,8,4,7,1,5,0,3,2
        */

        ModelDispatcher baseModel = Factory.makeModelDispatcher();

        // man[w] is the man chosen for woman w
        IntVar[] man = baseModel.intVarArray(n, n);
        // woman[m] is the woman chosen for man m
        IntVar[] woman = baseModel.intVarArray(n, n);

        // manPref[w] is the preference of woman w for the man chosen for w
        IntVar[] manPref = baseModel.intVarArray(n, n + 1);
        // womanPref[m] is the preference of man m for the woman chosen for m
        IntVar[] womanPref = baseModel.intVarArray(n, n + 1);


        for (int w = 0; w < n; w++) {
            baseModel.add(eq(get(woman,man[w]), w)); // the woman of the man of woman w is w
            baseModel.add(eq(get(rankMen[w], man[w]),manPref[w]));
        }

        for (int m = 0; m < n; m++) {
            // the man of the woman of man m is m
            baseModel.add(eq(get(man,woman[m]), m));
            baseModel.add(eq(get(rankWomen[m],woman[m]),womanPref[m]));
        }

        for (int w = 0; w < n; w++) {
            for (int m = 0; m < n; m++) {
                // if woman w prefers man m over the chosen man, then the opposite is not true: m prefers their chosen woman over w
                // (manPref[w] > rankMen[w][m]) => (womanPref[m] < rankWomen[m][w])
                baseModel.add(implies(ge(manPref[w], rankMen[w][m]), le(womanPref[m], rankWomen[m][w])));
            }
        }

        long time = TimeIt.run(() -> {
            baseModel.runCP(() -> {
                SearchMethod search = baseModel.dfSearch(and(Searches.firstFailBinary(man), Searches.firstFailBinary(woman)));
                search.onSolution(() -> {
                    System.out.println("-----");
                    System.out.println(Arrays.toString(man));
                    System.out.println(Arrays.toString(woman));
                });
                SearchStatistics stats = search.solve();
                System.out.println(stats);
            });
        });
        System.out.println(time);

    }

}