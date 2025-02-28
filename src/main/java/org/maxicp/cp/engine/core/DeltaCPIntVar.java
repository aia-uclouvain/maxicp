/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import java.util.Iterator;

/**
 * Object that allows to retrieve
 * in a constraint the changes of the domain
 * of a variable from one call to the {@code Constraint.propagate}
 * to the next.
 * This is also called the delta set.
 * This functionality is useful to implement
 * incremental filtering algorithm in some global constraints
 * that rely on the changes of the domains.
 *
 * @see CPIntVar#delta(CPConstraint)  for the creation.
 */
public interface DeltaCPIntVar extends Delta, Iterable<Integer> {

    /**
     * The variable related to this delta set
     *
     * @return the variable related to this delta set
     */
    CPIntVar variable();

    /**
     * The old min of the domain in previous call
     * to Constraint#propagate
     *
     * @return the previous minimum
     */
    int oldMin();

    /**
     * The old max of the domain in previous call
     * to Constraint#propagate
     *
     * @return the previous maximum of the domain
     */
    public int oldMax();

    /**
     * The old size of the domain in previous call
     * to Constraint#propagate
     *
     * @return the previous size of the domain
     */
    public int oldSize();

    /**
     * Tells if the domain has changed since previous call
     * to Constraint#propagate
     *
     * @return if the domain has changed
     */
    public boolean changed();

    /**
     * The size of the delta set since previous call
     * to Constraint#propagate
     *
     * @return the size of the delta set
     */
    public int size();

    /**
     * Tells if the minimum has changed in the domain
     * since previous call to Constraint#propagate
     *
     * @return
     */
    public boolean minChanged();

    /**
     * Tells if the maximum has changed in the domain
     * since previous call to Constraint#propagate
     *
     * @return
     */
    public boolean maxChanged();

    /**
     * An iterator on the delta set
     * This iterator is not computed lazily
     *
     * @return an iterator on the delta set
     */
    public Iterator<Integer> iterator();

    /**
     * Fill the prefix of the array with
     * the values in the delta set.
     *
     * @param values the array to fill, its size should be large enough
     *               that is at least oldSize-current domain size.
     * @return the size of the prefix that contains the delta set
     */
    public int fillArray(int [] values);

}
