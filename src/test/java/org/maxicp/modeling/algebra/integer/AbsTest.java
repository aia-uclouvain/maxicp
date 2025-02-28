package org.maxicp.modeling.algebra.integer;

import org.junit.Test;
import org.maxicp.modeling.Factory;
import org.maxicp.ModelDispatcher;

import java.util.HashSet;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AbsTest {
    void check(int min, int max) {
        assertTrue(min <= max);
        ModelDispatcher baseModel = Factory.makeModelDispatcher();

        IntExpression abs = Factory.abs(baseModel.intVar(min, max));

        HashSet<Integer> expectedContent = new HashSet<>();
        for(int i = min; i <= max; i++)
            expectedContent.add(Math.abs(i));

        assertTrue(expectedContent.size() <= abs.size()); //.size() is an upper bound!
        int[] absContent = new int[abs.size()];
        int size = abs.fillArray(absContent);
        assertTrue(size <= absContent.length); //.size() is an upper bound!

        for(int i = 0; i < size; i++) {
            assertTrue(expectedContent.contains(absContent[i]));
            expectedContent.remove(absContent[i]);
        }
        assertEquals(0, expectedContent.size());
    }

    @Test
    public void fillArray() {
        Random random = new Random(80982);

        check(0, 0); //single 0
        check(10, 10); //single positive
        check(-10, -10); //single negative

        for(int i = 0; i < 1000; i++) {
            int a = random.nextInt(10000)+1;
            int b = random.nextInt(10000)+1;
            check(0, a); //positive, 0 included
            check(a, a+b); //positive, 0 excluded
            check(-a, 0); //negative, 0 included
            check(-a-b, -b); //negative, 0 excluded
            check(-a, b); //both
        }
    }
}
