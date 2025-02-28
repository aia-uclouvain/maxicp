package org.maxicp.modeling.constraints;

import org.junit.Test;
import org.maxicp.modeling.Factory;
import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.algebra.sequence.SeqExpression;
import org.maxicp.modeling.constraints.helpers.ConstraintFromRecord;
import org.maxicp.util.ImmutableSet;

import static org.junit.Assert.assertEquals;

/**
 * Tests the ConstraintFromRecord class, ensuring it finds all variables and ignores basic types
 */
public class ConstraintFromRecordTest {
    @Test
    public void testFindType() {
        record CustomClass() {}
        record MyHorribleConstraint(IntExpression x,
                                    SeqExpression y,
                                    int t,
                                    float f,
                                    double d,
                                    char c,
                                    long l,
                                    int[][] aaa,
                                    int[] bbb,
                                    IntExpression[] tx,
                                    SeqExpression[][] ty,
                                    ImmutableSet<IntExpression> isx,
                                    @IgnoreScope CustomClass obj
                                    ) implements ConstraintFromRecord {}

        ModelDispatcher baseModel = Factory.makeModelDispatcher();

        IntExpression ie = baseModel.intVar(10, 100);
        SeqExpression se = baseModel.seqVar(10, 0, 5);

        MyHorribleConstraint cst = new MyHorribleConstraint(ie, se, 0, 0.0f, 0.0, 'c', 10,
                new int[][]{new int[]{1, 1}},
                new int[]{1, 1},
                new IntExpression[]{ie},
                new SeqExpression[][]{new SeqExpression[]{se}},
                ImmutableSet.of(ie),
                new CustomClass());

        assertEquals(2, cst.scope().size());
    }
}
