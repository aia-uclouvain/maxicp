/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.concrete.ConcreteVar;

/**
 * A set variable is a variable that represents a set of integers from an original universe set.
 * Its domain is conceptually represented by:
 * - a set of included integers I
 * - a set of possible integers P
 * - a set of excluded integers S
 * I,P and S are disjoint sets and their union is the universe of the set variable.
 * In addition, a cardinality variable represents the cardinality of the set
 * The variable is considered fixed when the set of possible integers is empty.
 * At this point, the cardinality variable is equal to the size of the set of included integers.
 */
public interface CPSetVar extends CPVar, ConcreteVar {

    /**
     * Returns a variable that represents the cardinality of the set.
     * @return the cardinality variable
     */
    CPIntVar card();

    /**
     * Returns if the set variable is fixed.
     * @return true if the set variable is fixed, false otherwise
     */
    boolean isFixed();

    /**
     * Returns the size of the set of included elements
     * @return the size of the set of included elements
     */
    int nIncluded();

    /**
     * Returns the size of the set of possible elements
     * @return the size of the set of possible elements
     */
    int nPossible();

    /**
     * Returns the size of the set of excluded elements
     * @return the size of the set of excluded elements
     */
    int nExcluded();

    /**
     * Returns if a value is included in the set.
     * @param v the value to check
     * @return true if the value is included, false otherwise
     */
    boolean isIncluded(int v);

    /**
     * Returns if a value is possible in the set.
     * @param v the value to check
     * @return true if the value is possible, false otherwise
     */
    boolean isPossible(int v);

    /**
     * Returns if a value is excluded from the set.
     * @param v the value to check
     * @return true if the value is excluded, false otherwise
     */
    boolean isExcluded(int v);

    /**
     * Includes a value in the set.
     * @param v the value to include, it must be a possible value from the universe otherwise an exception is thrown.
     *        The method has no effect if the value is already included.
     */
    void include(int v);

    /**
     * Excludes a value from the set.
     * @param v the value to exclude, it must be a possible value from the universe otherwise an exception is thrown
     *          The method has no effect if the value is already excluded.
     */
    void exclude(int v);

    /**
     * Includes all the possible values in the set.
     */
    void includeAll();

    /**
     * Excludes all the possible values from the set.
     */
    void excludeAll();

    /**
     * Copies the values (in an arbitrary order) of the set of included elements into an array.
     * @param dest an array large enough {@code dest.length >= nIncluded()}
     * @return the size of the set of included elements
     */
    int fillIncluded(int[] dest);

    /**
     * Copies the values (in an arbitrary order) of the set of possible elements into an array.
     * @param dest an array large enough {@code dest.length >= nPossible()}
     * @return the size of the set of possible elements
     */
    int fillPossible(int[] dest);

    /**
     * Copies the values (in an arbitrary order) of the set of excluded elements into an array.
     * @param dest an array large enough {@code dest.length >= nExcluded()}
     * @return the size of the set of excluded elements
     */
    int fillExcluded(int[] dest);

    int size();

    /**
     * Ask that the {@link CPConstraint#propagate()} method of the constraint c
     * is called when the domain of the set variable changes (I,P or E).
     * If intererested also in the change of the cardinality variable, call
     * {@link CPIntVar#propagateOnBoundChange(CPConstraint)} on the cardinality variable.
     * @param c the constraint to notify
     */
    void propagateOnDomainChange(CPConstraint c);

    /**
     * Returns the solver of the set variable.
     * @return the solver of the set variable
     */
    CPSolver getSolver();

    @Override
    ModelProxy getModelProxy();


}
