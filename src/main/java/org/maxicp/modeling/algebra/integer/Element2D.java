package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public record Element2D(int[][] array, IntExpression x, IntExpression y) implements SymbolicIntExpression, NonLeafExpressionNode {

    private int[] getTempArray(IntExpression index) {
        int minIndex = Math.max(index.min(), 0);
        int maxIndex = Math.min(index.max(), array.length - 1);
        return new int[maxIndex - minIndex + 1];
    }

    @Override
    public Collection<Expression> computeSubexpressions() {
        return List.of(x, y);
    }

    @Override
    public Element2D mapSubexpressions(Function<Expression, Expression> f) {
        return new Element2D(array, (IntExpression) f.apply(x), (IntExpression) f.apply(y));
    }

    @Override
    public int defaultEvaluate() throws VariableNotFixedException {
        return array[x.evaluate()][y.evaluate()];
    }

    @Override
    public int defaultMin() {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempx);
        int minValue = Integer.MAX_VALUE;
        for(int i = 0; i < sizex; i++)
            for(int j = 0; j < sizey; j++)
                minValue = Math.min(minValue, array[tempx[i]][tempy[j]]);
        return minValue;
    }

    @Override
    public int defaultMax() {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempx);
        int maxValue = Integer.MIN_VALUE;
        for(int i = 0; i < sizex; i++)
            for(int j = 0; j < sizey; j++)
                maxValue = Math.max(maxValue, array[tempx[i]][tempy[j]]);
        return maxValue;
    }

    @Override
    public boolean defaultContains(int v) {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempx);
        for(int i = 0; i < sizex; i++)
            for(int j = 0; j < sizey; j++)
                if (array[tempx[i]][tempy[j]] == v)
                    return true;
        return false;
    }

    @Override
    public int defaultFillArray(int[] array) {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempx);
        int k = 0;
        Set<Integer> s = new HashSet<>();
        for(int i = 0; i < sizex; i++) {
            for(int j = 0; j < sizey; j++) {
                int e = this.array[tempx[i]][tempy[j]];
                s.add(e);
                if (s.size() != k) {
                    array[k] = e;
                    k++;
                }
            }
        }
        return k;
    }

    @Override
    public int defaultSize() {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempx);
        Set<Integer> s = new HashSet<>();
        for(int i = 0; i < sizex; i++)
            for (int j = 0; j < sizey; j++)
                s.add(array[tempx[i]][tempy[j]]);
        return s.size();
    }

    @Override
    public String toString() {
        return show();
    }
}
