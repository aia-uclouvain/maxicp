package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public record Element1D(int[] array, IntExpression index) implements SymbolicIntExpression, NonLeafExpressionNode {

    private int[] getTempArray() {
        int minIndex = Math.max(index.min(), 0);
        int maxIndex = Math.min(index.max(), array.length - 1);
        return new int[maxIndex - minIndex + 1];
    }

    @Override
    public Collection<Expression> computeSubexpressions() {
        return List.of(index);
    }

    @Override
    public Element1D mapSubexpressions(Function<Expression, Expression> f) {
        return new Element1D(array, (IntExpression) f.apply(index));
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        return array[index.evaluate()];
    }

    @Override
    public int defaultMin() {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        int minValue = Integer.MAX_VALUE;
        for (int i = 0; i < size; i++)
            minValue = Math.min(minValue, array[temp[i]]);
        return minValue;
    }

    @Override
    public int defaultMax() {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        int maxValue = Integer.MIN_VALUE;
        for (int i = 0; i < size; i++)
            maxValue = Math.max(maxValue, array[temp[i]]);
        return maxValue;
    }

    @Override
    public boolean defaultContains(int v) {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        for (int i = 0; i < size; i++)
            if (this.array[temp[i]] == v)
                return true;
        return false;
    }

    @Override
    public int defaultFillArray(int[] array) {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        int j = 0;
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i < size; i++) {
            int e = this.array[temp[i]];
            s.add(e);
            if (s.size() != j) {
                array[j] = e;
                j++;
            }
        }
        return j;
    }

    @Override
    public int defaultSize() {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i < size; i++)
            s.add(this.array[temp[i]]);
        return s.size();
    }

    @Override
    public String toString() {
        return show();
    }
}
