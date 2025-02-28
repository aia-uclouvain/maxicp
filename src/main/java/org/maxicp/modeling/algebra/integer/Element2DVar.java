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

public record Element2DVar(IntExpression[][] array, IntExpression x,
                           IntExpression y) implements SymbolicIntExpression, NonLeafExpressionNode {

    private int[] getTempArray(IntExpression index) {
        int minIndex = Math.max(index.min(), 0);
        int maxIndex = Math.min(index.max(), array.length - 1);
        return new int[maxIndex - minIndex + 1];
    }

    @Override
    public Collection<Expression> computeSubexpressions() {
        ImmutableSet.Builder<Expression> b = ImmutableSet.builder();
        for (IntExpression[] a : array)
            b.add(a);
        b.add(x);
        b.add(y);
        return b.build();
    }

    @Override
    public Element2DVar mapSubexpressions(Function<Expression, Expression> f) {
        IntExpression[][] newArray = Arrays.stream(array).map(subarray ->
                Arrays.stream(subarray).map(entry -> (IntExpression) f.apply(entry)).toArray(IntExpression[]::new)
        ).toArray(IntExpression[][]::new);
        IntExpression newX = (IntExpression) f.apply(x);
        IntExpression newY = (IntExpression) f.apply(y);
        return new Element2DVar(newArray, newX, newY);
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        return array[x.evaluate()][y.evaluate()].evaluate();
    }

    @Override
    public int defaultMin() {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempx);
        int minValue = Integer.MAX_VALUE;
        for (int i = 0; i < sizex; i++)
            for (int j = 0; j < sizey; j++)
                minValue = Math.min(minValue, array[tempx[i]][tempy[j]].min());
        return minValue;
    }

    @Override
    public int defaultMax() {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempx);
        int maxValue = Integer.MIN_VALUE;
        for (int i = 0; i < sizex; i++)
            for (int j = 0; j < sizey; j++)
                maxValue = Math.max(maxValue, array[tempx[i]][tempy[j]].max());
        return maxValue;
    }

    @Override
    public boolean defaultContains(int v) {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempx);
        for (int i = 0; i < sizex; i++)
            for (int j = 0; j < sizey; j++)
                if (array[tempx[i]][tempy[j]].contains(v))
                    return true;
        return false;
    }

    @Override
    public int defaultFillArray(int[] array) {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempx);
        int l = 0;
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i < sizex; i++) {
            for (int j = 0; j < sizey; j++) {
                IntExpression e = this.array[tempx[i]][tempy[j]];
                int[] temp2 = new int[e.max() - e.min() + 1];
                int size2 = e.fillArray(temp2);
                for (int k = 0; k < size2; k++) {
                    s.add(temp2[k]);
                    if (s.size() != l) {
                        array[l] = temp2[k];
                        l++;
                    }
                }
            }
        }
        return l;
    }

    @Override
    public int defaultSize() {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempx);
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i < sizex; i++) {
            for (int j = 0; j < sizey; j++) {
                IntExpression e = this.array[tempx[i]][tempy[j]];
                int[] temp2 = new int[e.max() - e.min() + 1];
                int size2 = e.fillArray(temp2);
                for (int k = 0; k < size2; k++) {
                    s.add(temp2[k]);
                }
            }
        }
        return s.size();
    }

    @Override
    public String toString() {
        return show();
    }
}
