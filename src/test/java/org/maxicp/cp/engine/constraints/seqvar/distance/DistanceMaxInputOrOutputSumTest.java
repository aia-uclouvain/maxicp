package org.maxicp.cp.engine.constraints.seqvar.distance;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.constraints.seqvar.Distance;
import org.maxicp.cp.engine.constraints.seqvar.TransitionTimes;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSeqVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.examples.raw.distance.TSPTWBench;
import org.maxicp.modeling.algebra.sequence.SeqStatus;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.maxicp.cp.CPFactory.makeIntVar;
import static org.maxicp.cp.CPFactory.makeSolver;

class DistanceMaxInputOrOutputSumTest extends DistanceTest {
    @Override
    protected CPConstraint getDistanceConstraint(CPSeqVar seqVar, int[][] transitions, CPIntVar distance) {
        return new DistanceMaxInputOrOutputSum(seqVar, transitions, distance);
    }

//    @ParameterizedTest
//    @CsvSource(useHeadersInDisplayName = true, textBlock = """
//            nNodes, seed
//                5,     1
//                5,     2
//                5,     3
//                5,     4
//                5,     5
//                5,     6
//                5,     7
//                5,     8
//                5,     9
//                5,     10
//                5,     11
//                5,     12
//                5,     13
//                5,     14
//                5,     15
//                5,     16
//                5,     17
//                5,     18
//                5,     19
//                5,     20
//                6,     1
//                6,     2
//                6,     3
//                6,     4
//                6,     5
//                6,     6
//                6,     7
//                6,     8
//                6,     9
//                6,     10
//                6,     11
//                6,     12
//                6,     13
//                6,     14
//                6,     15
//                6,     16
//                6,     17
//                6,     18
//                6,     19
//                6,     20
//                7,     1
//                7,     2
//                7,     3
//                7,     4
//                7,     5
//                7,     6
//                7,     7
//                7,     8
//                7,     9
//                7,     10
//                7,     11
//                7,     12
//                7,     13
//                7,     14
//                7,     15
//                7,     16
//                7,     17
//                7,     18
//                7,     19
//                7,     20
//                8,     1
//                8,     2
//                8,     3
//                8,     4
//                8,     5
//                8,     6
//                8,     7
//                8,     8
//                8,     9
//                8,     10
//                8,     11
//                8,     12
//                8,     13
//                8,     14
//                8,     15
//                8,     16
//                8,     17
//                8,     18
//                8,     19
//                8,     20
//                9,     1
//                9,     2
//                9,     3
//                9,     4
//                9,     5
//                9,     6
//                9,     7
//                9,     8
//                9,     9
//                9,    10
//                9,    11
//                9,    12
//                9,    13
//                9,    14
//                9,    15
//                9,    16
//                9,    17
//                9,    18
//                9,    19
//                9,    20
//                10,    1
//                10,    2
//                10,    3
//                10,    4
//                10,    5
//                10,    6
//                10,    7
//                10,    8
//                10,    9
//                10,   10
//                10,   11
//                10,   12
//                10,   13
//                10,   14
//                10,   15
//                10,   16
//                10,   17
//                10,   18
//                10,   19
//                10,   20
//                15,   1
//                15,   2
//                15,   3
//                15,   4
//
//            """)
//    public void testTSPMinIOvsMaxIO(int nNodes, int seed) {
//        // instance data
//        Random random = new Random(seed);
//        int[][] transitions1 = randomTransitions(random, nNodes);
//        int[][] transitions2 = new int[transitions1.length][transitions1[0].length];
//        for (int i = 0; i < transitions1.length; i++) {
//            System.arraycopy(transitions1[i], 0, transitions2[i], 0, transitions1[i].length);
//        }
//        int roughUpperBound = Arrays.stream(transitions1).mapToInt(arr -> Arrays.stream(arr).max().getAsInt()).sum();
//
//        // model
//        CPSolver cp1 = makeSolver();
//        CPSolver cp2 = makeSolver();
//        CPSeqVar seqVar1 = CPFactory.makeSeqVar(cp1, nNodes, nNodes - 2, nNodes - 1);
//        CPSeqVar seqVar2 = CPFactory.makeSeqVar(cp2, nNodes, nNodes - 2, nNodes - 1);
//
//        for (int node = 0; node < nNodes; node++) {
//            seqVar1.require(node);
//            seqVar2.require(node);
//        }
//        CPIntVar distance1 = CPFactory.makeIntVar(cp1, 0, roughUpperBound);
//        CPIntVar distance2 = CPFactory.makeIntVar(cp2, 0, roughUpperBound);
//
////        CPIntVar[] time1 = new CPIntVar[nNodes];
////        CPIntVar[] time2 = new CPIntVar[nNodes];
////        for (int node = 0; node < nNodes; node++) {
////            if (node==seqVar1.start()||node==seqVar2.end() || node>=nNodes){
////                time1[node] = makeIntVar(cp1, 0, roughUpperBound);
////                time2[node] = makeIntVar(cp2, 0, roughUpperBound);
////                continue;
////            }
////            int earliest = random.nextInt(0, roughUpperBound/2);
////            int latest = random.nextInt(earliest*2, roughUpperBound);
////            time1[node] = makeIntVar(cp1, earliest, latest);
////            time2[node] = makeIntVar(cp2, earliest, latest);
////        }
//
//        // ===================== constraints =====================
//
////        cp1.post(new TransitionTimes(seqVar1, time1, transitions1));
//        cp1.post(new DistanceMinInputAndOutputSum(seqVar1, transitions1, distance1));
//
////        cp2.post(new TransitionTimes(seqVar2, time2, transitions2));
//        cp2.post(getDistanceConstraint(seqVar2, transitions2, distance2));
//        // compare the 2 searches
//
//
//        assertTrue(distance1.min() >= distance2.min());
//    }
//
//
    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            nNodes, seed
                5,     1
                5,     2
                5,     3
                5,     4
                5,     5
                5,     6
                5,     7
                5,     8
                5,     9
                5,     10
                5,     11
                5,     12
                5,     13
                5,     14
                5,     15
                5,     16
                5,     17
                5,     18
                5,     19
                5,     20
                8,     1
                8,     2
                8,     3
                8,     4
                8,     5
                8,     6
                8,     7
                8,     8
                8,     9
                8,     10
                8,     11
                8,     12
                8,     13
                8,     14
                8,     15
                8,     16
                8,     17
                8,     18
                8,     19
                8,     20
                10,    1
                10,    2
                10,    3
                10,    4
                10,    5
                10,    6
                10,    7
                10,    8
                10,    9
                10,   10
                10,   11
                10,   12
                10,   13
                10,   14
                10,   15
                10,   16
                10,   17
                10,   18
                10,   19
                10,   20
                15,   1
                15,   2
                15,   3
                15,   4
                15,   5
                15,   6
                15,   7
                15,   8
                15,   9
                15,   10
                20,   1
                20,   2
                20,   3
                20,   4
                20,   5
                20,   6
                20,   7
                20,   8
                20,   9
                20,   10

            """)
    public void testTSPVersus(int nNodes, int seed) {

        List<String> methods = new ArrayList<>();
        methods.add("noLowerBound");
        methods.add("MinDetourSum");
        methods.add("MaxInputOrOutputSum");
        methods.add("MinRestriqtedDetourSum");
        List<Integer> lowerBounds = new ArrayList<>();

        // instance data
        Random random = new Random(seed);
        int[][] transitions = randomTransitions(random, nNodes);
        int roughUpperBound = Arrays.stream(transitions).mapToInt(arr -> Arrays.stream(arr).max().getAsInt()).sum();

        for(String method : methods){
            int[][] transitionsUsed = new int[transitions.length][transitions[0].length];

            for (int i = 0; i < transitions.length; i++) {
                System.arraycopy(transitions[i], 0, transitionsUsed[i], 0, transitions[i].length);
            }

            // model
            CPSolver cp = makeSolver();
            CPSeqVar seqVar = CPFactory.makeSeqVar(cp, nNodes, nNodes - 2, nNodes - 1);

            for (int node = 0; node < nNodes; node++) {
                seqVar.require(node);
            }
            CPIntVar distance = CPFactory.makeIntVar(cp, 0, roughUpperBound);

            // ===================== constraints =====================

            if(method.equals("MinDetourSum")){
                cp.post(new DistanceMinDetourSum(seqVar, transitions, distance));
            } else if(method.equals("MaxInputOrOutputSum")){
                cp.post(new DistanceMaxInputOrOutputSum(seqVar, transitions, distance));
            } else if(method.equals("MinRestriqtedDetourSum")){
                cp.post(new DistanceMSTDetour(seqVar, transitions, distance));
            } else if(method.equals("noLowerBound")){
//                cp.post(new DistanceNoLowerBound(seqVar, transitions, distance));
            } else {
                throw new IllegalArgumentException("Unknown method: " + method);
            }

            lowerBounds.add(distance.min());
        }

//        System.out.println(lowerBounds);
        int LBMax = Collections.max(lowerBounds);
//        System.out.println(LBMax);
        System.out.println(methods.get(lowerBounds.indexOf(LBMax)));

//        assertTrue(distance1.min() >= distance2.min());
    }


}