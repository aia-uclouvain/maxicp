package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.util.ImmutableSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public record Element1DVar(IntExpression[] array, IntExpression index) implements SymbolicIntExpression, NonLeafExpressionNode {


    private int[] getTempArray() {
        int minIndex = Math.max(index.min(), 0);
        int maxIndex = Math.min(index.max(), array.length - 1);
        return new int[maxIndex - minIndex + 1];
    }

    @Override
    public Collection<Expression> computeSubexpressions() {
        ImmutableSet.Builder<Expression> b = ImmutableSet.builder();
        b.add(array);
        b.add(index);
        return b.build();
    }

    @Override
    public Element1DVar mapSubexpressions(Function<Expression, Expression> f) {
        return new Element1DVar(Arrays.stream(array).map(x -> (IntExpression)f.apply(x)).toArray(IntExpression[]::new), (IntExpression) f.apply(index));
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        return array[index.evaluate()].evaluate();
    }

    @Override
    public int defaultMin() {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        int minValue = Integer.MAX_VALUE;
        for (int i = 0; i < size; i++)
            minValue = Math.min(minValue, array[temp[i]].min());
        return minValue;
    }

    @Override
    public int defaultMax() {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        int maxValue = Integer.MIN_VALUE;
        for (int i = 0; i < size; i++)
            maxValue = Math.max(maxValue, array[temp[i]].max());
        return maxValue;
    }

    @Override
    public boolean defaultContains(int v) {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        for (int i = 0; i < size; i++)
            if (this.array[temp[i]].contains(v))
                return true;
        return false;
    }

    @Override
    public int defaultFillArray(int[] array) {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        int k = 0;
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i < size; i++) {
            IntExpression e = this.array[temp[i]];
            int[] temp2 = new int[e.max() - e.min() + 1];
            int size2 = e.fillArray(temp2);
            for (int j = 0; j < size2; j++){
                s.add(temp2[j]);
                if (s.size() != k) {
                    array[k] = temp2[j];
                    k++;
                }
            }
        }
        return k;
    }

    @Override
    public int defaultSize() {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i < size; i++){
            IntExpression e = this.array[temp[i]];
            int[] temp2 = new int[e.max() - e.min() + 1];
            int size2 = e.fillArray(temp2);
            for (int j = 0; j < size2; j++)
                s.add(temp2[j]);
        }
        return s.size();
    }

    @Override
    public String toString() {
        return show();
    }
}
