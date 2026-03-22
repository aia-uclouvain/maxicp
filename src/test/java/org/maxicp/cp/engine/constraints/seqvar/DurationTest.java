package org.maxicp.cp.engine.constraints.seqvar;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.algebra.sequence.SeqStatus;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;

public class DurationTest extends CPSolverTest  {


    @ParameterizedTest
    @MethodSource("getSolver")
    public void testAllDiffDisjunctive(CPSolver cp) {
        int n = 5; // omitting start and end
        CPIntervalVar[] intervals = new CPIntervalVar[n + 2];
        for (int i = 0; i < n + 2; i++) {
            intervals[i] = makeIntervalVar(cp);
            intervals[i].setPresent();
            intervals[i].setLength(1);
            intervals[i].setEndMax(n+2);
        }
        CPSeqVar seqVar = makeSeqVar(cp, n + 2, n, n+1);
        cp.post(new Duration(seqVar, intervals));
        DFSearch dfs = CPFactory.makeDfs(cp, Searches.firstFailBinary(seqVar));
        SearchStatistics stats = dfs.solve();
        assertEquals(120, stats.numberOfSolutions(), "disjunctive alldiff expect an array permutations");
    }

    private CPIntervalVar[] makeIntervals(CPSolver cp, int[] startMin, int[] endMax, int duration) {
        int n = startMin.length;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp);
            intervals[i].setStartMin(startMin[i]);
            intervals[i].setLength(duration);
            intervals[i].setEndMax(endMax[i]);
        }
        return intervals;
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOneNodeReachable(CPSolver cp) {
        int[] startMin = new int[] {
                0,
                0,
                0,
                0,
                0,
                100,
        };
        int[] endMax = new int[] {
                300,
                5,
                5,
                5,
                5,
                200
        };
        int n = startMin.length;
        CPIntervalVar[] intervals = makeIntervals(cp, startMin, endMax, 5);
        CPSeqVar seqVar = makeSeqVar(cp, n, 4, 5);
        cp.post(new Duration(seqVar, intervals));
        assertTrue(intervals[4].isPresent());
        assertTrue(intervals[5].isPresent());
        assertTrue(seqVar.isNode(0, SeqStatus.INSERTABLE));
        assertEquals(5, intervals[0].startMin());
        assertEquals(195, intervals[0].endMax());
        for (int i = 1 ; i <= 3 ; i++) {
            assertTrue(seqVar.isNode(i, SeqStatus.EXCLUDED));
        }
        cp.post(insert(seqVar, seqVar.start(), 0));
        assertTrue(intervals[0].isPresent());
        // 4 -> 0 -> 5
        assertEquals(0, intervals[4].startMin());
        assertEquals(5, intervals[0].startMin());
        assertEquals(100, intervals[5].startMin());

        assertEquals(200, intervals[5].endMax());
        assertEquals(195, intervals[0].endMax());
        assertEquals(5, intervals[4].endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testOneNodeUnreachable(CPSolver cp) {
        int[] startMin = new int[] {
                0,
                12,
                0,
                4,
                0,
                100,
        };
        int[] endMax = new int[] {
                25,
                21,
                25,
                8, // node 3 unreachable from the seqVar (distance from start: 8)
                25,
                205
        };
        int n = startMin.length;
        CPIntervalVar[] intervals = new CPIntervalVar[n];
        for (int i = 0; i < n; i++) {
            intervals[i] = makeIntervalVar(cp);
            intervals[i].setStartMin(startMin[i]);
            if (i >= 4) { // start or end
                intervals[i].setLength(10);
            } else {
                intervals[i].setLength(5);
            }
            intervals[i].setEndMax(endMax[i]);
        }
        CPSeqVar seqVar = makeSeqVar(cp, n, 4, 5);
        cp.post(new Duration(seqVar, intervals));
        assertTrue(seqVar.isNode(3, SeqStatus.EXCLUDED));
        for (int i = 0 ; i < 3 ; i++)
            assertFalse(seqVar.isNode(i, SeqStatus.EXCLUDED));
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void testMandatoryDetours(CPSolver cp) {
        int n = 11;
        CPSeqVar seqVar = makeSeqVar(cp, n, 0, 5);
        seqVar.insert(0, 1);
        seqVar.insert(1, 2);
        seqVar.insert(2, 3);
        seqVar.insert(3, 4);
        seqVar.notBetween(0, 6, 1);
        seqVar.notBetween(3, 6, 5);
        seqVar.notBetween(2, 7, 5);
        seqVar.notBetween(0, 8, 2);
        seqVar.notBetween(4, 8, 5);
        seqVar.notBetween(0, 9, 1);
        seqVar.notBetween(3, 9, 5);
        for (int i = 6 ; i < 10 ; i++) {
            seqVar.require(i);
        }

        /*
                 +-6-9-+
                 |     |
        0  -> 1  -> 2  -> 3  -> 4  -> 5
           |     |     |     |
           +--7--+     +--8--+

        nodes 6 to 9 are required and have only 2 insertions left
        nodes 10 can be inserted anywhere for now but will be forced to be inserted at the end
         */

        int[] startMin = new int[] {
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
        };
        int[] endMax = new int[] {
                100,
                100,
                100,
                100,
                100,
                100,
                100,
                100,
                100,
                100,
                100,
        };
        CPIntervalVar[] intervals = makeIntervals(cp, startMin, endMax, 5);
        cp.post(new Duration(seqVar, intervals));
        int[] startMinExpected = new int[] {
                0,
                5,
                15, // node 2: node 7 must be inserted before
                30, // node 3: node 6 and 9 must be inserted before
                40, // node 4: node 8 must be inserted before
                45,
                10, // node 6: at least 2 nodes before
                5,
                20, // node 8: at least 4 nodes before
                10, // node 9: at least 2 nodes before
                5,
        };
        int[] endMaxExpected = new int[] {
                55, // node 0: node 7 must be inserted after
                65, // node 1: node 6 and 9 must be inserted after
                80, // node 2: node 8 must be inserted after
                90,
                95,
                100,
                85, // node 6: at the latest before node 3
                75, // node 7: at the latest before node 2
                90, // node 8: at the latest before node4
                85, // node 9: at the latest before node 3
                95,
        };
        for (int i = 0 ; i < n ; i++) {
            assertEquals(startMinExpected[i], intervals[i].startMin());
            assertEquals(endMaxExpected[i], intervals[i].endMax());
        }

        cp.post(notBetween(seqVar, 0, 10, 4));
        assertEquals(45, intervals[10].startMin());
        assertEquals(95, intervals[10].endMax());


    }

}
