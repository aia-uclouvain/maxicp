package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.DecisionVarsProvider;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.Expression;
import org.maxicp.modeling.algebra.NonLeafExpressionNode;
import org.maxicp.modeling.algebra.VariableNotFixedException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public record Element1D(int[] array, IntExpression index) implements SymbolicIntExpression, NonLeafExpressionNode, DecisionVarsProvider {

    private int[] getTempArray() {
        return new int[Math.max(0, index.size())];
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
        for (int i = 0; i < size; i++) {
            int idx = temp[i];
            if (idx >= 0 && idx < array.length) {
                minValue = Math.min(minValue, array[idx]);
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
                maxValue = Math.max(maxValue, array[idx]);
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
            if (idx >= 0 && idx < array.length && this.array[idx] == v)
                return true;
        }
        return false;
    }

    @Override
    public int defaultFillArray(int[] array) {
        int[] temp = this.getTempArray();
        int size = index.fillArray(temp);
        int j = 0;
        Set<Integer> s = new HashSet<>();
        for (int i = 0; i < size; i++) {
            int idx = temp[i];
            if (idx < 0 || idx >= this.array.length) continue;
            int e = this.array[idx];
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
        for (int i = 0; i < size; i++) {
            int idx = temp[i];
            if (idx >= 0 && idx < this.array.length) {
                s.add(this.array[idx]);
            }
        }
        return s.size();
    }

    /** The index variable is the decision variable; the array data and result are constant/derived. */
    @Override
    public Collection<IntExpression> decisionVariables() {
        return (index instanceof IntVar) ? List.of(index) : List.of();
    }

    @Override
    public String toString() {
        return show();
    }
}
