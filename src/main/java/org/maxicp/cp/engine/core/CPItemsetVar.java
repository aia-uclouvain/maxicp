/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.cp.engine.core.mining.TransactionDb;
import org.maxicp.modeling.ModelProxy;
import org.maxicp.modeling.concrete.ConcreteVar;

/**
 * An itemset pattern variable: a first-class variable whose value is an itemset
 * (a set of items) interpreted against a {@link TransactionDb}.
 * <p>
 * Its structural domain is a tri-partition of the item universe:
 * - a set of required items R (the committed kernel of the pattern),
 * - a set of possible items P,
 * - a set of excluded items X.
 * The domain denotes all itemsets p with {@code R ⊆ p ⊆ R ∪ P}.
 * The variable is fixed when P is empty; its value is then R.
 * <p>
 * In addition to the structural domain, the variable maintains a reversible
 * occurrence kernel: the cover of R, that is the set of transactions containing
 * all required items. Constraints query the kernel through {@link #coverSize()},
 * {@link #supportAfter(int)} and {@link #coverSubsetOf(int)} without accessing
 * its internal representation.
 */
public interface CPItemsetVar extends CPVar, ConcreteVar {

    /**
     * Returns the transaction database against which the pattern is interpreted.
     *
     * @return the transaction database
     */
    TransactionDb database();

    /**
     * Returns the number of item identifiers of the universe.
     *
     * @return the number of item identifiers
     */
    int nItems();

    /**
     * Returns if the variable is fixed, that is no possible item remains.
     *
     * @return true if the variable is fixed
     */
    boolean isFixed();

    /**
     * Returns the number of required items.
     *
     * @return the number of required items
     */
    int nRequired();

    /**
     * Returns the number of possible items.
     *
     * @return the number of possible items
     */
    int nPossible();

    /**
     * Returns the number of excluded items.
     *
     * @return the number of excluded items
     */
    int nExcluded();

    /**
     * Returns if an item is required.
     *
     * @param i the item
     * @return true if the item is required
     */
    boolean isRequired(int i);

    /**
     * Returns if an item is possible.
     *
     * @param i the item
     * @return true if the item is possible
     */
    boolean isPossible(int i);

    /**
     * Returns if an item is excluded.
     *
     * @param i the item
     * @return true if the item is excluded
     */
    boolean isExcluded(int i);

    /**
     * Requires an item: every itemset of the domain contains it.
     * The cover is intersected with the transactions containing the item.
     *
     * @param i the item to require; throws an inconsistency if the item is excluded.
     *          The method has no effect if the item is already required.
     */
    void require(int i);

    /**
     * Excludes an item: no itemset of the domain contains it.
     *
     * @param i the item to exclude; throws an inconsistency if the item is required.
     *          The method has no effect if the item is already excluded.
     */
    void exclude(int i);

    /**
     * Requires all the possible items.
     */
    void requireAll();

    /**
     * Excludes all the possible items.
     */
    void excludeAll();

    /**
     * Copies the required items into an array.
     *
     * @param dest an array large enough {@code dest.length >= nRequired()}
     * @return the number of required items
     */
    int fillRequired(int[] dest);

    /**
     * Copies the possible items into an array.
     *
     * @param dest an array large enough {@code dest.length >= nPossible()}
     * @return the number of possible items
     */
    int fillPossible(int[] dest);

    /**
     * Copies the excluded items into an array.
     *
     * @param dest an array large enough {@code dest.length >= nExcluded()}
     * @return the number of excluded items
     */
    int fillExcluded(int[] dest);

    /**
     * Returns the size of the cover of the required items, that is the number of
     * transactions containing all required items. This is an upper bound on the
     * support of every itemset in the domain.
     *
     * @return the size of the current cover
     */
    int coverSize();

    /**
     * Returns the support of the required items extended with one more item,
     * that is {@code |cover(R ∪ {i})|}.
     *
     * @param i the extension item
     * @return the support of the pattern extended with i
     */
    int supportAfter(int i);

    /**
     * Returns the support of the required items extended with one more item,
     * stopping the count early as soon as the given cap is proved unreachable.
     * The returned value is exact when it is at least cap; otherwise it is a
     * value smaller than cap (the exact support is then also smaller than cap).
     *
     * @param i   the extension item
     * @param cap the threshold of interest
     * @return the possibly capped support of the pattern extended with i
     */
    int supportAfter(int i, int cap);

    /**
     * Returns if the current cover is a subset of the transactions containing an
     * item, that is if extending the pattern with the item leaves the cover unchanged.
     *
     * @param i the item
     * @return true if {@code cover(R) ⊆ cover({i})}
     */
    boolean coverSubsetOf(int i);

    /**
     * Returns the size of the cover of the required items extended with all the
     * possible items, that is {@code |cover(R ∪ P)|}. This is a lower bound on
     * the support of every itemset in the domain.
     *
     * @return the support of the largest itemset in the domain
     */
    int minSupport();

    /**
     * Returns a variable representing the number of items of the pattern.
     *
     * @return the size variable
     */
    CPIntVar size();

    /**
     * Returns a variable representing the support of the pattern, that is the
     * number of transactions containing all its items. The view is created lazily
     * on the first call; it must be requested before the search starts.
     *
     * @return the support variable
     */
    CPIntVar support();

    /**
     * Asks that the {@link CPConstraint#propagate()} method of the constraint c
     * is called when the domain of the variable changes (an item is required or excluded).
     *
     * @param c the constraint to notify
     */
    void propagateOnDomainChange(CPConstraint c);

    /**
     * Asks that the {@link CPConstraint#propagate()} method of the constraint c
     * is called when the variable becomes fixed.
     *
     * @param c the constraint to notify
     */
    void propagateOnFix(CPConstraint c);

    /**
     * Returns the solver of the variable.
     *
     * @return the solver of the variable
     */
    CPSolver getSolver();

    @Override
    ModelProxy getModelProxy();
}
