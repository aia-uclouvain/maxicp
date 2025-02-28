/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 */

package org.maxicp.cp.examples.raw;

import org.maxicp.cp.engine.constraints.Among;
import org.maxicp.cp.engine.constraints.CardinalityMaxFWC;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.IntStream;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.DFSearch.*;

/**
 * Nurse scheduling problem.
 * A hospital has a number of nurses that need to be scheduled for a number of services over a number of shifts.
 * A nurse can only be assigned to one service per shift
 * Each nurse must have at least minDaysOff days off and
 * each shift must have at least minExperiencedNursesPerShift experienced nurses.
 * An experienced nurse is a nurse with index in the set experiencedNurses.
 * The goal is to find a schedule that satisfies these constraints.
 * @author pschaus
 */
public class NurseScheduling {

    public static void main(String[] args) {
        CPSolver cp = makeSolver();

        int numNurses = 5; // Number of nurses
        int numServices = 3; // Hospital services (e.g., ER, ICU, Pediatrics)
        int numShifts = 7; // Days in the scheduling period
        int minDaysOff = 2; // Minimum number of days off for each nurse
        int minExperiencedNursesPerShift = 2; // Minimum experienced nurses at each shift
        Set<Integer> experiencedNurses = Set.of(0, 2, 4);

        CPIntVar[][] schedule = new CPIntVar[numServices][numShifts];
        for (int service = 0; service < numServices; service++) {
            for (int shift = 0; shift < numShifts; shift++) {
                schedule[service][shift] = makeIntVar(cp, 0, numNurses); // Nurse 0 to (numNurses - 1) or dummyNurse
            }
        }

        for (int shift = 0; shift < numShifts; shift++) {
            CPIntVar[] shiftNurses = new CPIntVar[numServices];
            for (int service = 0; service < numServices; service++) {
                shiftNurses[service] = schedule[service][shift];
            }
            int [] maxCard = IntStream.range(0, numNurses).map(i -> 1).toArray();
            cp.post(new CardinalityMaxFWC(shiftNurses, maxCard)); // a nurse can only be assigned to one service

            // Each shift must have at least minExperiencedNursesPerShift experienced nurses
            cp.post(new Among(shiftNurses, experiencedNurses, makeIntVar(cp, minExperiencedNursesPerShift, numNurses)));
        }

        // Each nurse must have at least minDaysOff days off
        CPIntVar[] flatSchedule = Arrays.stream(schedule).flatMap(Arrays::stream).toArray(CPIntVar[]::new);
        int [] maxCard = IntStream.range(0, numNurses).map(i -> numShifts-minDaysOff).toArray();
        cp.post(new CardinalityMaxFWC(flatSchedule, maxCard));


        // Search strategy: assign shifts to nurses
        DFSearch search = makeDfs(cp, () -> {
            for (int service = 0; service < numServices; service++) {
                for (int shift = 0; shift < numShifts; shift++) {
                    if (schedule[service][shift].isFixed()) continue;
                    final CPIntVar nurse = schedule[service][shift];
                    final int n = nurse.min();
                    return Searches.branch(() -> cp.post(equal(nurse, n)),
                            () -> cp.post(notEqual(nurse, n)));
                }
            }
            return Searches.EMPTY;
        });

        search.onSolution(() -> {
            System.out.println("Solution:");
            for (int service = 0; service < numServices; service++) {
                System.out.print("Service " + service +":"+ Arrays.toString(schedule[service]) + "\n");
            }
            System.out.println();
        });

        // Start search
        search.solve(searchStatistics ->  searchStatistics.numberOfSolutions() > 0);
    }
}
