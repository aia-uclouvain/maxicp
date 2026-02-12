/*
 * MaxiCP is under MIT License
 * Copyright (c)  2025 UCLouvain
 *
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.AllDifferentDC;
import org.maxicp.cp.engine.constraints.CardinalityMaxFWC;
import org.maxicp.cp.engine.constraints.CardinalityMinFWC;
import org.maxicp.cp.engine.constraints.TableCT;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.maxicp.cp.CPFactory.*;

/**
 * The problem is to schedule an even number n of teams over n/2 periods and n - 1 weeks,
 * under the following constraints: <br>
 *  - Each team must play against every other team <br>
 *  - A team plays exactly one game per week  <br>
 *  - A team can play at most twice in the same period <br>
 *
 * @author Pierre Schaus pschaus@gmail.com
 */
public class SportScheduling {
    public static void main(String[] args) {

        CPSolver cp = makeSolver(false);

        int n = 14;
        int nbPeriods = n / 2;
        int nbTeams = n;
        int nbWeeks = n - 1;

        // ---- variables ----

        CPIntVar[][][] team = new CPIntVar[nbPeriods][nbWeeks][2];
        for (int p = 0; p < nbPeriods; p++) {
            for (int w = 0; w < nbWeeks; w++) {
                team[p][w][0] = makeIntVar(cp,0, nbTeams - 1);
                team[p][w][1] = makeIntVar(cp,0, nbTeams - 1);
            }
        }

        CPIntVar[][] game = new CPIntVar[nbPeriods][nbWeeks];
        for (int p = 0; p < nbPeriods; p++) {
            for (int w = 0; w < nbWeeks; w++) {
                game[p][w] = makeIntVar(cp, 0, nbTeams * nbTeams);
            }
        }

        // Create tuples for valid games
        int [][] tuples = new int[nbTeams * (nbTeams - 1) / 2][];
        int t = 0;
        for (int i = 0; i < nbTeams; i++) {
            for (int j = i + 1; j < nbTeams; j++) {
                tuples[t] = new int[]{i, j, i * nbTeams + j};
                t++;
            }
        }

        // ---- constraints -----

        // Make the link between the team and game variables
        for (int p = 0; p < nbPeriods; p++) {
            for (int w = 0; w < nbWeeks; w++) {
                // Add the table constraint linking team and game variables
                cp.post(new TableCT(new CPIntVar[]{team[p][w][0], team[p][w][1], game[p][w]}, tuples));
            }
        }

        // A team plays exactly one game per week
        IntStream.range(0, nbWeeks).forEach(w -> {
                CPIntVar[] weekVars = Arrays.stream(team)
                        .flatMap(period -> Arrays.stream(period[w]))
                        .toArray(CPIntVar[]::new);
                cp.post(new AllDifferentDC(weekVars));
        });

        // Every team plays against every other team
        CPIntVar [] allGames = Arrays.stream(game).flatMap(Arrays::stream).toArray(CPIntVar[]::new);
        cp.post(new AllDifferentDC(allGames));

        // A team can play at most twice in the same period
        IntStream.range(0,nbPeriods).forEach(p -> {

            CPIntVar[] periodTeams = IntStream.range(0, nbWeeks).boxed().
                    flatMap(w -> IntStream.range(0, 2).mapToObj(h -> team[p][w][h])).toArray(CPIntVar[]::new);

            int[] minCard = new int[nbTeams];
            Arrays.fill(minCard, 1);
            cp.post(new CardinalityMinFWC(periodTeams, minCard));

            int[] maxCard = new int[nbTeams];
            Arrays.fill(maxCard, 2);
            cp.post(new CardinalityMaxFWC(periodTeams, maxCard));
        });


        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFailBinary(allGames));
        search.onSolution(() -> {
            System.out.println("---------games---------");
            for (int p = 0; p < nbPeriods; p++) {
                for (int w = 0; w < nbWeeks; w++) {
                    System.out.print(game[p][w].min() + "\t");
                }
                System.out.println();
            }

            System.out.println("---------teams---------");
            for (int p = 0; p < nbPeriods; p++) {
                for (int w = 0; w < nbWeeks; w++) {
                    System.out.print("(" + team[p][w][0].min() + "," + team[p][w][1].min() + ")\t");
                }
                System.out.println();
            }
        });

        SearchStatistics stats = search.solve(searchStatistics -> searchStatistics.numberOfSolutions() > 0);
        System.out.println(stats);

    }
}
