package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.util.ImmutableSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public record Element1DVar(IntExpression[] array, IntExpression index) implements SymbolicIntExpression, NonLeafExpressionNode, DecisionVarsProvider {


    private int[] getTempArray() {
        return new int[Math.max(0, index.size())];
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
        for (int i = 0; i < size; i++) {
            int idx = temp[i];
            if (idx >= 0 && idx < array.length) {
                minValue = Math.min(minValue, array[idx].min());
            }
        }
        return minValue;
    }

    @Override
    public int defaultMax() {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        int maxValue = Integer.MIN_VALUE;
        for (int i = 0; i < size; i++) {
            int idx = temp[i];
            if (idx >= 0 && idx < array.length) {
                maxValue = Math.max(maxValue, array[idx].max());
            }
        }
        return maxValue;
    }

    @Override
    public boolean defaultContains(int v) {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        for (int i = 0; i < size; i++) {
            int idx = temp[i];
            if (idx >= 0 && idx < array.length && this.array[idx].contains(v))
                return true;
        }
        return false;
    }

    @Override
    public int defaultFillArray(int[] array) {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        int k = 0;
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i < size; i++) {
            int idx = temp[i];
            if (idx < 0 || idx >= this.array.length) continue;
            IntExpression e = this.array[idx];
            int[] temp2 = new int[Math.max(0, e.size())];
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
            int idx = temp[i];
            if (idx < 0 || idx >= this.array.length) continue;
            IntExpression e = this.array[idx];
            int[] temp2 = new int[Math.max(0, e.size())];
            int size2 = e.fillArray(temp2);
            for (int j = 0; j < size2; j++)
                s.add(temp2[j]);
        }
        return s.size();
    }

    /** The index variable selects the row; it is the decision variable. */
    @Override
    public Collection<IntExpression> decisionVariables() {
        return (index instanceof IntVar) ? List.of(index) : List.of();
    }

    @Override
    public String toString() {
        return show();
    }
}
