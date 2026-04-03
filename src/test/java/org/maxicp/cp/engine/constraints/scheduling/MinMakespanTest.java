package org.maxicp.cp.engine.constraints.scheduling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;

import static org.maxicp.cp.CPFactory.makeIntVar;
import static org.maxicp.cp.CPFactory.makeIntervalVar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MinMakespan constraint implementing Jackson's preemptive schedule
 * based on Carlier and Pinson (1990).
 * 
 * The constraint computes a lower bound on makespan using the Jackson preemptive schedule,
 * which processes tasks by earliest deadline first (EDF) policy, allowing preemption.
 */
public class MinMakespanTest  extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOK(CPSolver cp) {
        CPIntervalVar intervalVar1 = makeIntervalVar(cp);
        CPIntervalVar intervalVar2 = makeIntervalVar(cp);
        CPIntervalVar intervalVar3 = makeIntervalVar(cp);
        CPIntervalVar intervalVar4 = makeIntervalVar(cp);
        CPIntervalVar intervalVar5 = makeIntervalVar(cp);
        CPIntervalVar intervalVar6 = makeIntervalVar(cp);

        CPIntervalVar[] intervals = new CPIntervalVar[]{intervalVar1, intervalVar2, intervalVar3, intervalVar4, intervalVar5, intervalVar6};
        intervalVar1.setLength(6);
        intervalVar2.setLength(8);
        intervalVar3.setLength(4);
        intervalVar4.setLength(5);
        intervalVar5.setLength(8);
        intervalVar6.setLength(8);
        intervalVar1.setStartMin(4);
        intervalVar2.setStartMin(0);
        intervalVar3.setStartMin(9);
        intervalVar4.setStartMin(15);
        intervalVar5.setStartMin(20);
        intervalVar6.setStartMin(21);
        intervalVar1.setEndMax(52-20);
        intervalVar2.setEndMax(52-25);
        intervalVar3.setEndMax(52-30);
        intervalVar4.setEndMax(52-9);
        intervalVar5.setEndMax(52-14);
        intervalVar6.setEndMax(52-16);

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        graph.setTail(20, intervalVar1);
        graph.setTail(25, intervalVar2);
        graph.setTail(30, intervalVar3);
        graph.setTail(9, intervalVar4);
        graph.setTail(14, intervalVar5);
        graph.setTail(16, intervalVar6);

        CPIntVar obj = makeIntVar(cp,0,100000);
        cp.post(new MinMakespan(graph, obj, intervals));
        assertEquals(obj.min(), 50);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSimpleSequentialJobs(CPSolver cp) {
        CPIntervalVar job1 = makeIntervalVar(cp);
        CPIntervalVar job2 = makeIntervalVar(cp);
        CPIntervalVar job3 = makeIntervalVar(cp);

        CPIntervalVar[] intervals = new CPIntervalVar[]{job1, job2, job3};
        
        job1.setLength(10);
        job2.setLength(5);
        job3.setLength(8);
        
        job1.setStartMin(0);
        job2.setStartMin(0);
        job3.setStartMin(0);
        
        job1.setEndMax(100);
        job2.setEndMax(100);
        job3.setEndMax(100);

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        graph.setTail(0, job1);
        graph.setTail(0, job2);
        graph.setTail(0, job3);

        CPIntVar makespan = makeIntVar(cp, 0, 100000);
        cp.post(new MinMakespan(graph, makespan, intervals));
        
        assertEquals(23, makespan.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testPreemptionWithReleaseTimesAndDeadlines(CPSolver cp) {
        CPIntervalVar job1 = makeIntervalVar(cp);
        CPIntervalVar job2 = makeIntervalVar(cp);
        CPIntervalVar job3 = makeIntervalVar(cp);

        CPIntervalVar[] intervals = new CPIntervalVar[]{job1, job2, job3};
        
        job1.setLength(4);
        job2.setLength(3);
        job3.setLength(2);
        
        job1.setStartMin(0);
        job2.setStartMin(1);
        job3.setStartMin(3);
        
        job1.setEndMax(15);
        job2.setEndMax(8);
        job3.setEndMax(10);

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        graph.setTail(0, job1);
        graph.setTail(7, job2);
        graph.setTail(5, job3);

        CPIntVar makespan = makeIntVar(cp, 0, 100000);
        cp.post(new MinMakespan(graph, makespan, intervals));
        
        assertTrue(makespan.min() >= 9);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSingleJob(CPSolver cp) {
        CPIntervalVar job = makeIntervalVar(cp);
        CPIntervalVar[] intervals = new CPIntervalVar[]{job};
        
        job.setLength(15);
        job.setStartMin(5);
        job.setEndMax(100);

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        graph.setTail(10, job);

        CPIntVar makespan = makeIntVar(cp, 0, 100000);
        cp.post(new MinMakespan(graph, makespan, intervals));
        
        assertEquals(30, makespan.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testIdenticalJobs(CPSolver cp) {
        CPIntervalVar job1 = makeIntervalVar(cp);
        CPIntervalVar job2 = makeIntervalVar(cp);
        CPIntervalVar job3 = makeIntervalVar(cp);

        CPIntervalVar[] intervals = new CPIntervalVar[]{job1, job2, job3};
        
        job1.setLength(5);
        job2.setLength(5);
        job3.setLength(5);
        
        job1.setStartMin(0);
        job2.setStartMin(0);
        job3.setStartMin(0);
        
        job1.setEndMax(50);
        job2.setEndMax(50);
        job3.setEndMax(50);

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        graph.setTail(0, job1);
        graph.setTail(0, job2);
        graph.setTail(0, job3);

        CPIntVar makespan = makeIntVar(cp, 0, 100000);
        cp.post(new MinMakespan(graph, makespan, intervals));
        
        assertEquals(15, makespan.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testJobsWithTightDeadlines(CPSolver cp) {
        CPIntervalVar job1 = makeIntervalVar(cp);
        CPIntervalVar job2 = makeIntervalVar(cp);
        CPIntervalVar job3 = makeIntervalVar(cp);

        CPIntervalVar[] intervals = new CPIntervalVar[]{job1, job2, job3};
        
        job1.setLength(3);
        job2.setLength(2);
        job3.setLength(4);
        
        job1.setStartMin(0);
        job2.setStartMin(0);
        job3.setStartMin(0);
        
        job1.setEndMax(5);
        job2.setEndMax(3);
        job3.setEndMax(10);

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        graph.setTail(0, job1);
        graph.setTail(3, job2);
        graph.setTail(0, job3);

        CPIntVar makespan = makeIntVar(cp, 0, 100000);
        cp.post(new MinMakespan(graph, makespan, intervals));
        
        assertTrue(makespan.min() >= 5);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testStaggeredReleaseTimesEDF(CPSolver cp) {
        CPIntervalVar job1 = makeIntervalVar(cp);
        CPIntervalVar job2 = makeIntervalVar(cp);
        CPIntervalVar job3 = makeIntervalVar(cp);
        CPIntervalVar job4 = makeIntervalVar(cp);

        CPIntervalVar[] intervals = new CPIntervalVar[]{job1, job2, job3, job4};
        
        job1.setLength(2);
        job2.setLength(3);
        job3.setLength(4);
        job4.setLength(1);
        
        job1.setStartMin(0);
        job2.setStartMin(2);
        job3.setStartMin(4);
        job4.setStartMin(6);
        
        job1.setEndMax(20);
        job2.setEndMax(18);
        job3.setEndMax(16);
        job4.setEndMax(14);

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        graph.setTail(12, job1);
        graph.setTail(11, job2);
        graph.setTail(10, job3);
        graph.setTail(7, job4);

        CPIntVar makespan = makeIntVar(cp, 0, 100000);
        cp.post(new MinMakespan(graph, makespan, intervals));
        
        assertTrue(makespan.min() >= 14);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testLongAndShortJobs(CPSolver cp) {
        CPIntervalVar shortJob1 = makeIntervalVar(cp);
        CPIntervalVar shortJob2 = makeIntervalVar(cp);
        CPIntervalVar longJob = makeIntervalVar(cp);

        CPIntervalVar[] intervals = new CPIntervalVar[]{shortJob1, shortJob2, longJob};
        
        shortJob1.setLength(1);
        shortJob2.setLength(1);
        longJob.setLength(10);
        
        shortJob1.setStartMin(0);
        shortJob2.setStartMin(0);
        longJob.setStartMin(0);
        
        shortJob1.setEndMax(5);
        shortJob2.setEndMax(6);
        longJob.setEndMax(20);

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        graph.setTail(0, shortJob1);
        graph.setTail(0, shortJob2);
        graph.setTail(0, longJob);

        CPIntVar makespan = makeIntVar(cp, 0, 100000);
        cp.post(new MinMakespan(graph, makespan, intervals));
        
        assertEquals(12, makespan.min());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testJobsWithDifferentTailValues(CPSolver cp) {
        CPIntervalVar job1 = makeIntervalVar(cp);
        CPIntervalVar job2 = makeIntervalVar(cp);
        CPIntervalVar job3 = makeIntervalVar(cp);

        CPIntervalVar[] intervals = new CPIntervalVar[]{job1, job2, job3};
        
        job1.setLength(5);
        job2.setLength(3);
        job3.setLength(7);
        
        job1.setStartMin(0);
        job2.setStartMin(2);
        job3.setStartMin(4);
        
        job1.setEndMax(40);
        job2.setEndMax(35);
        job3.setEndMax(30);

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        graph.setTail(20, job1);
        graph.setTail(15, job2);
        graph.setTail(10, job3);

        CPIntVar makespan = makeIntVar(cp, 0, 100000);
        cp.post(new MinMakespan(graph, makespan, intervals));
        
        assertTrue(makespan.min() >= 25);
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testZeroLengthJob(CPSolver cp) {
        CPIntervalVar job1 = makeIntervalVar(cp);
        CPIntervalVar job2 = makeIntervalVar(cp);

        CPIntervalVar[] intervals = new CPIntervalVar[]{job1, job2};
        
        job1.setLength(0);
        job2.setLength(5);
        
        job1.setStartMin(0);
        job2.setStartMin(0);
        
        job1.setEndMax(10);
        job2.setEndMax(10);

        PrecedenceGraph graph = new PrecedenceGraph(intervals);
        graph.setTail(0, job1);
        graph.setTail(0, job2);

        CPIntVar makespan = makeIntVar(cp, 0, 100000);
        cp.post(new MinMakespan(graph, makespan, intervals));
        
        assertEquals(5, makespan.min());
    }
    @ParameterizedTest
    @MethodSource("getSolver")
    public void testSameNumberOfSol(CPSolver cp){

    }
}
