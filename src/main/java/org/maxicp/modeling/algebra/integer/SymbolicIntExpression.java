package org.maxicp.modeling.algebra.integer;

import org.maxicp.modeling.algebra.VariableNotFixedException;

public interface SymbolicIntExpression extends IntExpression {
    /**
     * Evaluate this expression. All variables referenced have to be fixed.
     *
     * @return the value of this expression
     * @throws VariableNotFixedException when a variable is not fixed
     */
    @Override
    default int evaluate() throws VariableNotFixedException {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).evaluate();
        return defaultEvaluate();
    }

    int defaultEvaluate() throws VariableNotFixedException;

    /**
     * Return a *lower bound* for this expression
     */
    @Override
    default int min() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).min();
        return defaultMin();
    }

    int defaultMin();

    /**
     * Return an *upper bound* for this expression
     */
    @Override
    default int max() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).max();
        return defaultMax();
    }


    int defaultMax();

    /**
     * Returns whether this expression *can* contain v.
     */
    @Override
    default boolean contains(int v) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).contains(v);
        return defaultContains(v);
    }

    default boolean defaultContains(int v) {
        return min() <= v && v <= max();
    }

    /**
     * Fill an array of minimum size size() with a *superset* of the domain of this expression.
     * Returns `v`, the size of the domain after it has been computed, with {@code v <= size()}.
     */
    @Override
    default int fillArray(int[] array) {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).fillArray(array);
        return defaultFillArray(array);
    }

    default int defaultFillArray(int[] array) {
        int vmin = min();
        int vmax = max();
        for (int i = 0; i <= vmax - vmin; i++)
            array[i] = vmin + i;
        return vmax - vmin + 1;
    }

    /**
     * *Upper bound* on the size of the domain of this expression.
     */
    @Override
    default int size() {
        if (getModelProxy().isConcrete())
            return getModelProxy().getConcreteModel().getConcreteVar(this).size();
        return defaultSize();
    }

    default int defaultSize() {
        return max() - min() + 1;
    }

    @Override
    default IntExpression plus(int v) {
        return new CstOffset(this, v);
    }

    @Override
    default IntExpression minus(int v) {
        return new CstOffset(this, -v);
    }

    @Override
    default IntExpression times(int v) {
        return new CstMul(this, v);
    }

    @Override
    default IntExpression plus(IntExpression v) {
        return new Sum(this, v);
    }

    @Override
    default IntExpression minus(IntExpression v) {
        return new Sum(this, new UnaryMinus(v));
    }

    @Override
    default IntExpression times(IntExpression v) {
        return new Mul(this, v);
    }

    @Override
    default IntExpression abs() {
        return new Abs(this);
    }
}
