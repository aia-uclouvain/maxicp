/*
 * MaxiCP is under MIT License
 * Copyright (c)  2024 UCLouvain
 *
 */

package org.maxicp.cp.modeling;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.maxicp.ModelDispatcher;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.constraints.scheduling.CPCumulFunction;
import org.maxicp.cp.engine.constraints.scheduling.CPFlatCumulFunction;
import org.maxicp.cp.engine.constraints.scheduling.CPPlusCumulFunction;
import org.maxicp.cp.engine.constraints.scheduling.CPPulseCumulFunction;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntervalVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.scheduling.CumulFunction;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.modeling.Factory.*;
import static org.maxicp.search.Searches.*;

public class CumulFunctionTest extends CPSolverTest {


    @Test
    public void simpleCapacity() {
        ModelDispatcher model = makeModelDispatcher();

        IntervalVar interval1 = model.intervalVar(0, 10, 2, true);
        IntervalVar interval2 = model.intervalVar(0, 10, 2, true);

        IntExpression start1 = Factory.start(interval1);
        IntExpression start2 = Factory.start(interval2);

        CumulFunction resource = sum(pulse(interval1,8),pulse(interval2,8));
        model.add(le(resource,8));
        model.add(eq(start1,0));

        ConcreteCPModel cp = model.cpInstantiate();

        Assertions.assertEquals(2, start2.min());
    }

    @Test
    public void testSameBehaviorRawAndModeling() {
        // Small cumulative scheduling problem:
        // 5 activities with fixed durations, one resource with capacity 3,
        // precedences: 0 -> 2, 1 -> 3

        int nActivities = 5;
        int[] duration = {2, 3, 2, 1, 4};
        int[] demand = {2, 1, 3, 2, 1};
        int capacity = 3;
        int[][] successors = {{2}, {3}, {}, {}, {}}; // 0->2, 1->3

        // ---- Raw model ----
        SearchStatistics rawStats;
        List<Integer> rawSolutions = new ArrayList<>();
        {
            CPSolver cp = makeSolver();
            CPIntervalVar[] tasks = makeIntervalVarArray(cp, nActivities);
            for (int i = 0; i < nActivities; i++) {
                tasks[i].setLength(duration[i]);
                tasks[i].setPresent();
            }

            CPCumulFunction resource = new CPFlatCumulFunction();
            for (int i = 0; i < nActivities; i++) {
                if (demand[i] > 0) {
                    resource = new CPPlusCumulFunction(resource, new CPPulseCumulFunction(tasks[i], demand[i]));
                }
            }
            cp.post(CPFactory.le(resource, capacity));

            for (int i = 0; i < nActivities; i++) {
                for (int k : successors[i]) {
                    cp.post(CPFactory.endBeforeStart(tasks[i], tasks[k]));
                }
            }

            CPIntVar makespan = CPFactory.makespan(tasks);
            org.maxicp.search.Objective rawObj = cp.minimize(makespan);

            DFSearch dfs = CPFactory.makeDfs(cp, fds(tasks));
            dfs.onSolution(() -> rawSolutions.add(makespan.min()));
            rawStats = dfs.optimize(rawObj);
        }



        // ---- Modeling layer ----
        SearchStatistics modelingStats;
        List<Integer> modelingSolutions = new ArrayList<>();
        {
            ModelDispatcher model = Factory.makeModelDispatcher();
            IntervalVar[] tasks = model.intervalVarArray(nActivities, true);
            for (int i = 0; i < nActivities; i++) {
                model.add(Factory.length(tasks[i], duration[i]));
            }

            CumulFunction resource = Factory.flat();
            for (int i = 0; i < nActivities; i++) {
                if (demand[i] > 0) {
                    resource = sum(resource, pulse(tasks[i], demand[i]));
                }
            }
            model.add(Factory.le(resource, capacity));

            for (int i = 0; i < nActivities; i++) {
                for (int k : successors[i]) {
                    model.add(endBeforeStart(tasks[i], tasks[k]));
                }
            }

            IntExpression makespan = max(java.util.Arrays.stream(tasks)
                    .map(task -> endOr(task, 0)).toArray(IntExpression[]::new));
            Objective obj = minimize(makespan);

            modelingStats = model.runCP((cp) -> {
                DFSearch search = cp.dfSearch(fds(tasks));
                search.onSolution(() -> modelingSolutions.add(makespan.min()));
                return search.optimize(obj);
            });
        }

        // Compare results
        Assertions.assertFalse(rawSolutions.isEmpty(), "Raw model should find at least one solution");
        Assertions.assertEquals(rawSolutions.size(), modelingSolutions.size(),
                "Same number of improving solutions");
        // Compare best (last) makespan found
        Assertions.assertEquals(rawSolutions.get(rawSolutions.size() - 1),
                modelingSolutions.get(modelingSolutions.size() - 1),
                "Same optimal makespan");
        // Compare number of backtracks (failures)
        Assertions.assertEquals(rawStats.numberOfFailures(), modelingStats.numberOfFailures(),
                "Same number of failures/backtracks");
        Assertions.assertEquals(rawStats.numberOfNodes(), modelingStats.numberOfNodes(),
                "Same number of nodes");

    }


    /*
    // buggy implementation
    @Test
    public void simpleCapacityWithEnergyConstraintOneActivity() {

        ModelDispatcher model = makeModelDispatcher();

        IntervalVar interval1 = model.intervalVar(0, 10, 0,10,1,10, true);

        IntExpression start1 = Factory.start(interval1);
        IntExpression end1 = Factory.end(interval1);
        IntExpression length1 = Factory.length(interval1);

        CumulFunction resource = pulse(interval1,1,10);

        IntExpression height1 = resource.heightAtStart(interval1);

        model.add(eq(mul(height1, length1), 10));

        model.add(eq(start1,0));
        model.add(eq(end1, 1)); // thus height1 = 10, it violates the capacity constraint belose

        model.add(le(resource,8));

        try {
            ConcreteCPModel cp = model.cpInstantiate();
            System.out.println(interval1);
            Assertions.fail();
        } catch (InconsistencyException e) {

        }
    }*/

    /*
    // buggy implementation
    @Test
    public void simpleCapacityWithEnergyConstraintTwoActivities() {

        ModelDispatcher model = makeModelDispatcher();

        IntervalVar interval1 = model.intervalVar(0, 10, 0,10,1,10, true);
        IntervalVar interval2 = model.intervalVar(0, 10, 0,10,1,10, true);

        IntExpression start1 = Factory.start(interval1);
        IntExpression end1 = Factory.end(interval1);
        IntExpression length1 = Factory.length(interval1);

        IntExpression start2 = Factory.start(interval2);
        IntExpression end2 = Factory.end(interval2);
        IntExpression length2 = Factory.length(interval2);

        CumulFunction resource = sum(pulse(interval1,1,10),pulse(interval2,1,10));

        IntExpression height1 = resource.heightAtStart(interval1);
        IntExpression height2 = resource.heightAtStart(interval2);

        model.add(eq(mul(height1, length1), 8));
        model.add(eq(mul(height2, length2), 8));

        model.add(eq(start1,0));
        model.add(eq(end1, 1)); // thus height1 = 8

        model.add(eq(length2,1)); // thus height2 = 8


        model.add(le(resource,8));

        ConcreteCPModel cp = model.cpInstantiate();

        Assertions.assertEquals(8, height1.min());
        Assertions.assertEquals(8, height2.min());

        Assertions.assertEquals(1, start2.min());


    }*/




}
