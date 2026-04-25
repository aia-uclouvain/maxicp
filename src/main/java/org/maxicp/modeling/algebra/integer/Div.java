package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.bool.BoolExpression;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Expression x / y
 * @param x
 * @param y
 */
public record Div(IntExpression x, IntExpression y) implements SymbolicIntExpression, NonLeafExpressionNode {

    @Override
    public Collection<? extends Expression> computeSubexpressions() {
        return List.of(x,y);
    }

    @Override
    public Div mapSubexpressions(Function<Expression, Expression> f) {
        return null;
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        return x.evaluate() / y.evaluate();
    }

    private static int safeDiv(int a, int b) {
        if (a == Integer.MIN_VALUE && b == -1) {
            return Integer.MAX_VALUE;
        }
        return a / b; // Java truncates toward 0
    }

    @Override
    public int defaultMin() {
        int xmin = x.min();
        int xmax = x.max();
        int ymin = y.min();
        int ymax = y.max();

        int min = Integer.MAX_VALUE;

        // Case 1: y entirely negative
        if (ymax < 0) {
            min = Math.min(min, safeDiv(xmin, ymin));
            min = Math.min(min, safeDiv(xmin, ymax));
            min = Math.min(min, safeDiv(xmax, ymin));
            min = Math.min(min, safeDiv(xmax, ymax));
        }
        // Case 2: y entirely positive
        else if (ymin > 0) {
            min = Math.min(min, safeDiv(xmin, ymin));
            min = Math.min(min, safeDiv(xmin, ymax));
            min = Math.min(min, safeDiv(xmax, ymin));
            min = Math.min(min, safeDiv(xmax, ymax));
        }
        // Case 3: y spans zero → split
        else {
            // negative part [ymin, -1]
            if (ymin <= -1) {
                min = Math.min(min, safeDiv(xmin, ymin));
                min = Math.min(min, safeDiv(xmin, -1));
                min = Math.min(min, safeDiv(xmax, ymin));
                min = Math.min(min, safeDiv(xmax, -1));
            }

            // positive part [1, ymax]
            if (ymax >= 1) {
                min = Math.min(min, safeDiv(xmin, 1));
                min = Math.min(min, safeDiv(xmin, ymax));
                min = Math.min(min, safeDiv(xmax, 1));
                min = Math.min(min, safeDiv(xmax, ymax));
            }
        }

        return min;
    }

    @Override
    public int defaultMax() {
        int xmin = x.min();
        int xmax = x.max();
        int ymin = y.min();
        int ymax = y.max();

        int max = Integer.MIN_VALUE;

        // Case 1: y entirely negative
        if (ymax < 0) {
            max = Math.max(max, safeDiv(xmin, ymin));
            max = Math.max(max, safeDiv(xmin, ymax));
            max = Math.max(max, safeDiv(xmax, ymin));
            max = Math.max(max, safeDiv(xmax, ymax));
        }
        // Case 2: y entirely positive
        else if (ymin > 0) {
            max = Math.max(max, safeDiv(xmin, ymin));
            max = Math.max(max, safeDiv(xmin, ymax));
            max = Math.max(max, safeDiv(xmax, ymin));
            max = Math.max(max, safeDiv(xmax, ymax));
        }
        // Case 3: y spans zero → split
        else {
            // negative part [ymin, -1]
            if (ymin <= -1) {
                max = Math.max(max, safeDiv(xmin, ymin));
                max = Math.max(max, safeDiv(xmin, -1));
                max = Math.max(max, safeDiv(xmax, ymin));
                max = Math.max(max, safeDiv(xmax, -1));
            }

            // positive part [1, ymax]
            if (ymax >= 1) {
                max = Math.max(max, safeDiv(xmin, 1));
                max = Math.max(max, safeDiv(xmin, ymax));
                max = Math.max(max, safeDiv(xmax, 1));
                max = Math.max(max, safeDiv(xmax, ymax));
            }
        }

        return max;
    }

    @Override
    public String toString() {
        return show();
    }
}