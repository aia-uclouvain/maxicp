package org.maxicp.cp.engine.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.CPSolverTest;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.engine.core.CPSeqVarAssertion.assertSeqVar;
import static org.maxicp.modeling.algebra.sequence.SeqStatus.*;

public class CPSeqVarViewFLipTest extends CPSolverTest {

    public static Stream<CPSeqVar> getSeqVar() {
        return getSolver().map(cp -> CPFactory.makeSeqVar(cp, nNodes, start, end));
    }

    static int nNodes = 12;
    static int start = 10;
    static int end = 11;

    /**
     * Tests for the insertions of nodes within a flipped sequence
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testInsert(CPSeqVar seqVar) {
        CPSeqVar flip = CPFactory.flip(seqVar);
        assertEquals(flip.start(), seqVar.end());
        assertEquals(flip.end(), seqVar.start());

        seqVar.insert(seqVar.start(), 5);
        seqVar.insert(seqVar.start(), 4);
        seqVar.insert(seqVar.start(), 3);
        int[] order = new int[nNodes];
        int[] orderFlipped = new int[nNodes];

        int nMember = seqVar.fillNode(order, MEMBER_ORDERED);
        int nMemberOpposite = flip.fillNode(orderFlipped, MEMBER_ORDERED);

        assertEquals(nMemberOpposite, nMember);

        for (int i = 0 ; i < nMember ; i++) {
            assertEquals(order[i], orderFlipped[nMember - i - 1]);
        }

        // order in original sequence:
        // 10 -> 3 -> 4 -> 5 -> 11

        flip.insert(3, 2);
        flip.insert(2, 1);

        // order in original sequence:
        // 10 -> 1 -> 2 -> 3 -> 4 -> 5 -> 11

        nMember = seqVar.fillNode(order, MEMBER_ORDERED);
        nMemberOpposite = flip.fillNode(orderFlipped, MEMBER_ORDERED);
        assertEquals(nMemberOpposite, nMember);
        int[] expected = new int[] {10, 1, 2, 3, 4, 5, 11};
        for (int i = 0 ; i < nMember ; i++) {
            assertEquals(order[i], orderFlipped[nMember - i - 1]);
            assertEquals(order[i], expected[i]);
        }
    }

    /**
     * Tests for the exclusions of nodes within a flipped sequence
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testExclude(CPSeqVar seqVar) {
        CPSeqVar flip = CPFactory.flip(seqVar);

        seqVar.exclude(2);
        assertTrue(flip.isNode(2, EXCLUDED));

        flip.exclude(4);
        assertTrue(seqVar.isNode(4, EXCLUDED));
    }

    /**
     * Tests for the requiring nodes within a flipped sequence
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testRequire(CPSeqVar seqVar) {
        CPSeqVar flip = CPFactory.flip(seqVar);

        seqVar.require(2);
        assertTrue(flip.isNode(2, REQUIRED));
        // has lead to the insertion of node 2
        assertEquals(2, flip.memberAfter(flip.start()));

        flip.require(4);
        assertTrue(seqVar.isNode(4, REQUIRED));
    }

    /**
     * Tests for the exclusions of nodes within a flipped sequence
     */
    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testNotBetween(CPSeqVar seqVar) {
        seqVar.insert(start, 3);
        seqVar.insert(start, 2);
        seqVar.insert(start, 1);
        // 10 -> 1 -> 2 -> 3 -> 11
        CPSeqVar flip = CPFactory.flip(seqVar);
        // flip: 11 -> 3 -> 2 -> 1 -> 10

        flip.notBetween(11, 4, 3);
        assertFalse(flip.hasInsert(11, 4));
        assertFalse(seqVar.hasInsert(3, 4));

        seqVar.notBetween(2, 5, 3);
        assertFalse(flip.hasInsert(3, 5));

        flip.notBetween(11, 6, 1);
        // 6 can only be inserted on edge 1 -> 10
        assertEquals(1, seqVar.nInsert(6));
        assertEquals(1, flip.nInsert(6));
        assertTrue(flip.hasInsert(1, 6));
        assertTrue(seqVar.hasInsert(10, 6));
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testInvariants(CPSeqVar seqVar) {
        seqVar.exclude(2);
        seqVar.exclude(4);

        CPSeqVar flip = CPFactory.flip(seqVar);

        int[] member1 = new int[]{end, start};
        int[] possible1 = new int[]{0, 1, 3, 5, 6, 7, 8, 9};
        int[] excluded1 = new int[]{2, 4};

        int[][] inserts1 = new int[][]{
                {end},
                {end},
                {},
                {end},
                {},
                {end},
                {end},
                {end},
                {end},
                {end},
                {},
                {},
        };
        assertSeqVar(flip, member1, possible1, excluded1, inserts1);

        seqVar.insert(start, 6);
        // start -> 6 -> end

        seqVar.notBetween(start, 3, seqVar.memberAfter(start));
        seqVar.notBetween(start, 7, seqVar.memberAfter(start));
        seqVar.notBetween(6, 8, seqVar.memberAfter(6));

        int[] member2 = new int[]{end, 6, start};
        int[] possible2 = new int[]{0, 1, 3, 5, 7, 8, 9};
        int[] excluded2 = new int[]{2, 4};
        int[][] predInsert2 = new int[][] {
                {end, 6},
                {end, 6},
                {},
                {end},
                {},
                {end, 6},
                {},
                {end},
                {6},
                {end, 6},
                {},
                {},
        };

        assertSeqVar(flip, member2, possible2, excluded2, predInsert2);

        seqVar.notBetween(6, 5, seqVar.memberAfter(6));
        seqVar.notBetween(start, 5, seqVar.memberAfter(start));
        seqVar.notBetween(start, 8, seqVar.memberAfter(start));
        seqVar.notBetween(start, 1, seqVar.memberAfter(start));
        seqVar.notBetween(6, 0, seqVar.memberAfter(6));

        int[] member3 = new int[]{end, 6, start};
        int[] possible3 = new int[]{0, 1, 3, 7, 9};
        int[] excluded3 = new int[]{2, 4, 8, 5};
        int[][] predInsert3 = new int[][] {
                {6},
                {end},
                {},
                {end},
                {},
                {},
                {},
                {end},
                {},
                {end, 6},
                {},
                {},
        };
        assertSeqVar(flip, member3, possible3, excluded3, predInsert3);

        CPNodeVar node9Flipped = flip.getNodeVar(9);
        int[] inserts = new int[2];
        int nInsert = node9Flipped.fillInsert(inserts);
        assertEquals(2, nInsert);

        Arrays.sort(inserts);
        assertArrayEquals(new int[] {6, end}, inserts);
    }

    @ParameterizedTest
    @MethodSource("getSeqVar")
    public void testSuccAndPred(CPSeqVar seqVar) {
        seqVar.insert(start, 1);
        seqVar.notBetween(start, 0, 1);
        int nPred = seqVar.nPred(0);
        int nSucc = seqVar.nSucc(0);
        int[] pred = new int[nPred];
        int[] succ = new int[nSucc];
        seqVar.fillPred(0, pred);
        seqVar.fillSucc(0, succ);

        CPSeqVar flip = CPFactory.flip(seqVar);
        int nPredFlip = flip.nPred(0);
        int nSuccFlip = flip.nSucc(0);
        int[] predFlip = new int[nPredFlip];
        int[] succFlip = new int[nSuccFlip];
        assertEquals(nPredFlip, flip.fillPred(0, predFlip));
        assertEquals(nSuccFlip, flip.fillSucc(0, succFlip));

        assertEquals(nPred, nSuccFlip);
        assertEquals(nSucc, nPredFlip);

        Arrays.sort(pred);
        Arrays.sort(succ);
        Arrays.sort(predFlip);
        Arrays.sort(succFlip);
        assertArrayEquals(pred, succFlip);
        assertArrayEquals(succ, predFlip);
    }

}
