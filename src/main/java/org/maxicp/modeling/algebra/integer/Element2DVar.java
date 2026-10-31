package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.util.ImmutableSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public record Element2DVar(IntExpression[][] array, IntExpression x,
                           IntExpression y) implements SymbolicIntExpression, NonLeafExpressionNode, DecisionVarsProvider {

    private int[] getTempArray(IntExpression index) {
        return new int[Math.max(0, index.size())];
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
        int sizey = y.fillArray(tempy);
        int minValue = Integer.MAX_VALUE;
        for (int i = 0; i < sizex; i++) {
            int rx = tempx[i];
            if (rx < 0 || rx >= array.length) continue;
            for (int j = 0; j < sizey; j++) {
                int cy = tempy[j];
                if (cy >= 0 && cy < array[rx].length) {
                    minValue = Math.min(minValue, array[rx][cy].min());
                }
            }
        }
        return minValue;
    }

    @Override
    public int defaultMax() {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempy);
        int maxValue = Integer.MIN_VALUE;
        for (int i = 0; i < sizex; i++) {
            int rx = tempx[i];
            if (rx < 0 || rx >= array.length) continue;
            for (int j = 0; j < sizey; j++) {
                int cy = tempy[j];
                if (cy >= 0 && cy < array[rx].length) {
                    maxValue = Math.max(maxValue, array[rx][cy].max());
                }
            }
        }
        return maxValue;
    }

    @Override
    public boolean defaultContains(int v) {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempy);
        for (int i = 0; i < sizex; i++) {
            int rx = tempx[i];
            if (rx < 0 || rx >= array.length) continue;
            for (int j = 0; j < sizey; j++) {
                int cy = tempy[j];
                if (cy >= 0 && cy < array[rx].length && array[rx][cy].contains(v))
                    return true;
            }
        }
        return false;
    }

    @Override
    public int defaultFillArray(int[] array) {
        int[] tempx = this.getTempArray(x);
        int sizex = x.fillArray(tempx);
        int[] tempy = this.getTempArray(y);
        int sizey = y.fillArray(tempy);
        int l = 0;
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i < sizex; i++) {
            int rx = tempx[i];
            if (rx < 0 || rx >= this.array.length) continue;
            for (int j = 0; j < sizey; j++) {
                int cy = tempy[j];
                if (cy < 0 || cy >= this.array[rx].length) continue;
                IntExpression e = this.array[rx][cy];
                int[] temp2 = new int[Math.max(0, e.size())];
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
        int sizey = y.fillArray(tempy);
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i < sizex; i++) {
            int rx = tempx[i];
            if (rx < 0 || rx >= array.length) continue;
            for (int j = 0; j < sizey; j++) {
                int cy = tempy[j];
                if (cy < 0 || cy >= array[rx].length) continue;
                IntExpression e = this.array[rx][cy];
                int[] temp2 = new int[Math.max(0, e.size())];
                int size2 = e.fillArray(temp2);
                for (int k = 0; k < size2; k++) {
                    s.add(temp2[k]);
                }
            }
        }
        return s.size();
    }

    /** Both row index x and column index y are decision variables; the result is derived. */
    @Override
    public Collection<IntExpression> decisionVariables() {
        List<IntExpression> result = new ArrayList<>(2);
        if (x instanceof IntVar) result.add(x);
        if (y instanceof IntVar) result.add(y);
        return List.copyOf(result);
    }

    @Override
    public String toString() {
        return show();
    }
}
