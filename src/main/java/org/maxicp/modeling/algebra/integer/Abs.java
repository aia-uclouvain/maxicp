package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public record Abs(IntExpression expr) implements SymbolicIntExpression, NonLeafExpressionNode {
    @Override
    public Collection<Expression> computeSubexpressions() {
        return List.of(expr);
    }

    @Override
    public Abs mapSubexpressions(Function<Expression, Expression> f) {
        return new Abs((IntExpression) f.apply(expr));
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        return Math.abs(expr.evaluate());
    }

    @Override
    public int defaultMin() {
        int m = expr.min();
        if (m >= 0)
            return m;
        m = expr.max();
        if (m <= 0)
            return -m;
        return 0;
    }

    @Override
    public int defaultMax() {
        return Math.max(Math.abs(expr.min()), Math.abs(expr.max()));
    }

    @Override
    public boolean defaultContains(int v) {
        return expr.contains(-v) || expr.contains(v);
    }

    @Override
    public int defaultFillArray(int[] array) {
        int v = expr.fillArray(array);
        Arrays.sort(array, 0, v);
        int lastNeg = -1;
        int pos = 0;
        for(int i = 0; i < v; i++) {
            if(array[i] < 0) {
                array[pos] = -array[i];
                lastNeg = pos;
                pos += 1;
            }
            else {
                //array[i] is positive, check for duplicates
                while (lastNeg != -1 && array[lastNeg] < array[i])
                    lastNeg--;
                if(lastNeg != -1 && array[lastNeg] == array[i])
                    continue;
                array[pos] = array[i];
                pos += 1;
            }
        }
        return pos;
    }

    @Override
    public int defaultSize() {
        return expr.size();
    }

    @Override
    public String toString() {
        return show();
    }
}
