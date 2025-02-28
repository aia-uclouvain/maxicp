/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.search;

import org.junit.jupiter.api.Test;
import org.maxicp.state.StateManagerTest;
import org.maxicp.util.NotImplementedExceptionAssume;
import org.maxicp.util.exception.NotImplementedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.maxicp.search.Searches.EMPTY;


public class SequencerTest extends StateManagerTest {
    @Test
    public void testExample1() {
        try {
            Sequencer seq = new Sequencer(SequencerTest::fakeSequencer0, SequencerTest::fakeSequencer1, SequencerTest::fakeSequencer2);

            state = 0;
            Runnable[] branches = seq.get();
            assertEquals(2, branches.length);
            branches[0].run();
            assertEquals(1, state);
            branches[1].run();
            assertEquals(2, state);

            state = 1;
            branches = seq.get();
            assertEquals(2, branches.length);
            branches[0].run();
            assertEquals(3, state);
            branches[1].run();
            assertEquals(4, state);

            state = 2;
            branches = seq.get();
            assertEquals(2, branches.length);
            branches[0].run();
            assertEquals(5, state);
            branches[1].run();
            assertEquals(6, state);

            state = 4;
            branches = seq.get();
            assertEquals(3, branches.length);
            branches[0].run();
            assertEquals(7, state);
            branches[1].run();
            assertEquals(8, state);
            branches[2].run();
            assertEquals(9, state);

            for(int s: new int[]{3, 5, 6, 7, 8, 9}) {
                state = s;
                branches = seq.get();
                assertEquals(0, branches.length);
            }
        } catch (NotImplementedException e) {
            NotImplementedExceptionAssume.fail(e);
        }
    }

    private static int state = 0;
    private static Runnable[] fakeSequencer0() {
        if(state == 0)
            return new Runnable[]{() -> {state = 1;}, () -> {state = 2;}};
        return EMPTY;
    }

    private static Runnable[] fakeSequencer1() {
        if(state == 1)
            return new Runnable[]{() -> {state = 3;}, () -> {state = 4;}};
        else if(state == 2)
            return new Runnable[]{() -> {state = 5;}, () -> {state = 6;}};
        return EMPTY;
    }

    private static Runnable[] fakeSequencer2() {
        if(state == 2)
            return new Runnable[]{() -> {state = 10;}};
        if(state == 4)
            return new Runnable[]{() -> {state = 7;}, () -> {state = 8;}, () -> {state = 9;}};
        return EMPTY;
    }
}
