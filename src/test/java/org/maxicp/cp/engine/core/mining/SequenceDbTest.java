/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core.mining;

import org.junit.jupiter.api.Test;
import org.maxicp.cp.CPSolverTest;

import static org.junit.jupiter.api.Assertions.*;

public class SequenceDbTest extends CPSolverTest {

    @Test
    public void testReadSpmf() {
        SequenceDb db = SequenceDb.readSpmf("data/MINING/SEQUENCE/test.spmf");
        assertEquals("test", db.name());
        assertEquals(4, db.nSeqs());
        assertEquals(6, db.nSymbols()); // max symbol is 5
        assertEquals(7, db.maxSeqLength());
        assertArrayEquals(new int[]{1, 2, 4, 3, 2}, db.sequence(0));
        assertArrayEquals(new int[]{2, 1, 1, 4, 3, 1, 2}, db.sequence(1));
        assertArrayEquals(new int[]{1, 2, 4, 4, 2, 5, 3}, db.sequence(2));
        assertArrayEquals(new int[]{1, 3, 3, 2}, db.sequence(3));
        assertEquals(0, db.symbolSupport(0));
        assertEquals(4, db.symbolSupport(1));
        assertEquals(4, db.symbolSupport(2));
        assertEquals(4, db.symbolSupport(3));
        assertEquals(3, db.symbolSupport(4));
        assertEquals(1, db.symbolSupport(5));
    }

    @Test
    public void testHandBuiltDb() {
        // db of the PPIC paper example: s1=abcbc, s2=babc, s3=ab, s4=bcd
        SequenceDb db = new SequenceDb("toy", new int[][]{
                {1, 2, 3, 2, 3},
                {2, 1, 2, 3},
                {1, 2},
                {2, 3, 4}});
        assertEquals("toy", db.name());
        assertEquals(4, db.nSeqs());
        assertEquals(5, db.nSymbols());
        assertEquals(5, db.maxSeqLength());
        assertEquals(3, db.symbolSupport(1));
        assertEquals(4, db.symbolSupport(2));
        assertEquals(3, db.symbolSupport(3));
        assertEquals(1, db.symbolSupport(4));

        // first/last positions are 1-based, 0 when absent
        assertEquals(1, db.firstPos(0, 1));
        assertEquals(2, db.firstPos(0, 2));
        assertEquals(3, db.firstPos(0, 3));
        assertEquals(0, db.firstPos(0, 4));
        assertEquals(1, db.lastPos(0, 1));
        assertEquals(4, db.lastPos(0, 2));
        assertEquals(5, db.lastPos(0, 3));
        assertEquals(0, db.lastPos(0, 4));
        assertEquals(2, db.firstPos(1, 1));
        assertEquals(1, db.firstPos(1, 2));
        assertEquals(2, db.lastPos(1, 1));
        assertEquals(3, db.lastPos(1, 2));
        assertEquals(4, db.lastPos(1, 3));

        // decreasing last positions of the distinct symbols
        assertArrayEquals(new int[]{5, 4, 1}, db.lastPosList(0));
        assertArrayEquals(new int[]{4, 3, 2}, db.lastPosList(1));
        assertArrayEquals(new int[]{2, 1}, db.lastPosList(2));
        assertArrayEquals(new int[]{3, 2, 1}, db.lastPosList(3));
    }

    @Test
    public void testEmptySequences() {
        SequenceDb db = new SequenceDb("empty", new int[][]{{}, {}});
        assertEquals(2, db.nSeqs());
        assertEquals(1, db.nSymbols());
        assertEquals(0, db.maxSeqLength());
        assertEquals(0, db.symbolSupport(0));
        assertArrayEquals(new int[]{}, db.lastPosList(0));
    }

    @Test
    public void testInvalidSymbol() {
        assertThrows(IllegalArgumentException.class, () -> new SequenceDb("bad", new int[][]{{1, 0, 2}}));
        assertThrows(IllegalArgumentException.class, () -> new SequenceDb("bad", new int[][]{{-3}}));
    }
}
