/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.cp.CPFactory;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.modeling.concrete.ConcreteIntVar;
import org.maxicp.util.exception.InconsistencyException;

import java.util.function.Consumer;

public interface CPIntVar extends CPVar, ConcreteIntVar {

    /**
     * Returns the solver in which this variable was created.
     *
     * @return the solver in which this variable was created
     */
    CPSolver getSolver();

    /**
     * Asks that the closure is called whenever the domain
     * of this variable is reduced to a single setValue.
     *
     * @param f the closure
     */
    void whenFixed(Runnable f);

    /**
     * Asks that the closure is called whenever
     * the max or min setValue of the domain of this variable changes
     *
     * @param f the closure
     */
    void whenBoundChange(Runnable f);

    /**
     * Asks that the closure is called whenever the domain change
     * of this variable changes
     *
     * @param f the closure
     */
    void whenDomainChange(Runnable f);


    /**
     * Asks that the consumer is called whenever the domain change
     * of this variable changes.
     * The consumer is called with a delta object that allows to retrieve
     * the changes in the domain of the variable (removed values) since
     * the previous call.
     *
     * @param f the consumer with the delta of the domain since last call
     */
    void whenDomainChange(Consumer<DeltaCPIntVar> f);

    /**
     * Asks that {@link CPConstraint#propagate()} is called whenever the domain
     * of this variable changes.
     * We say that a <i>change</i> event occurs.
     *
     * @param c the constraint for which the {@link CPConstraint#propagate()}
     *          method should be called on change events of this variable.
     */
    void propagateOnDomainChange(CPConstraint c);

    /**
     * Asks that {@link CPConstraint#propagate()} is called whenever the domain
     * of this variable is reduced to a singleton.
     * In such a state the variable is bind and we say that a <i>bind</i> event occurs.
     *
     * @param c the constraint for which the {@link CPConstraint#propagate()}
     *          method should be called on bind events of this variable.
     */
    void propagateOnFix(CPConstraint c);

    /**
     * Asks that {@link CPConstraint#propagate()} is called whenever the
     * bound (maximum or minimum values) of the domain
     * of this variable is changes.
     * We say that a <i>bound change</i> event occurs in this case.
     *
     * @param c the constraint for which the {@link CPConstraint#propagate()}
     *          method should be called on bound change events of this variable.
     */
    void propagateOnBoundChange(CPConstraint c);


    /**
     * Returns the minimum of the domain of the variable
     *
     * @return the minimum of the domain of the variable
     */
    int min();

    /**
     * Returns the maximum of the domain of the variable
     *
     * @return the maximum of the domain of the variable
     */
    int max();

    /**
     * Returns the size of the domain of the variable
     *
     * @return the size of the domain of the variable
     */
    int size();

    /**
     * Copies the values of the domain into an array.
     *
     * @param dest an array large enough {@code dest.length >= size()}
     * @return the size of the domain and {@code dest[0,...,size-1]} contains
     *         the values in the domain in an arbitrary order
     */
    int fillArray(int[] dest);

    /**
     * Returns true if the domain of the variable has a single value.
     *
     * @return true if the domain of the variable is a singleton.
     */
    boolean isFixed();

    /**
     * Returns true if the domain contains the specified value.
     * @param v the value whose presence in the domain is to be tested
     * @return true if the domain contains the specified value
     */
    boolean contains(int v);

    /**
     * Removes the specified value.
     * @param v the value to remove
     * @exception InconsistencyException
     *            is thrown if the domain becomes empty
     */
    void remove(int v);

    /**
     * Fixes the specified value.
     *
     * @param v the value to assign.
     * @exception InconsistencyException
     *            is thrown if the value is not in the domain
     */
    void fix(int v);

    /**
     * Removes all the values less than a given value.
     *
     * @param v the value such that all the values less than v are removed
     * @exception InconsistencyException
     *            is thrown if the domain becomes empty
     */
    void removeBelow(int v);

    /**
     * Removes all the values above a given value.
     *
     * @param v the value such that all the values larger than v are removed
     * @exception InconsistencyException
     *            is thrown if the domain becomes empty
     */
    void removeAbove(int v);

    /**
     * Copies the values of the domain that have been
     * removed (delta set) wrt to a previous state of the domain
     * described by oldMin, oldMax and oldSize.
     *
     * @param dest an array large enough {@code dest.length >= oldSize-size()}
     * @return the size of delta set stored in prefix of dest
     */
    int fillDeltaArray(int oldMin, int oldMax, int oldSize, int [] dest);

    /**
     * Returns a delta object allowing to retrieve the changes
     * in the domain of the variable (removed values) since
     * the previous call to the {@code Constraint.propagate} of the constraint.
     * This can be useful to implement some constraint with
     * incremental reasoning.
     *
     * @param c the constraint wrt the delta set is computed
     * @return the delta object
     */
    DeltaCPIntVar delta(CPConstraint c);

    default int evaluate() throws VariableNotFixedException {
        if (isFixed()) {return min();}
        throw new VariableNotFixedException();
    }

    default IntExpression plus(int v) {
        return CPFactory.plus(this, v);
    }

    default IntExpression minus(int v) {
        return CPFactory.plus(this, -v);
    }

    default IntExpression plus(IntExpression v) {
        if(v instanceof CPIntVar cpi)
            return CPFactory.sum(this, cpi);
        else
            throw new RuntimeException("Attempting to sum a CPIntVar with a SymbolicIntExpression");
    }

    default IntExpression minus(IntExpression v) {
        if(v instanceof CPIntVar cpi)
            return CPFactory.sum(this, CPFactory.minus(cpi));
        else
            throw new RuntimeException("Attempting to sum a CPIntVar with a SymbolicIntExpression");
    }

    default IntExpression abs() {
        return CPFactory.abs(this);
    }
}
