/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

/**
 * Interface for integer domain implementation.
 * A domain is encapsulated in an {@link CPIntVar} implementation.
 * A domain is like a set of integers.
 */
public interface IntDomain {

    /**
     * Returns the minimum value of the domain.
     *
     * @return the minimum value of the domain
     */
    int min();

    /**
     * Returns the maximum value of the domain.
     *
     * @return the maximum value of the domain
     */
    int max();

    /**
     * Returns the cardinality of the domain.
     *
     * @return the cardinality value of the domain
     */
    int size();

    /**
     * Checks if the specified value belongs to the domain.
     *
     * @param v the value to be tested
     * @return true if v belongs to the domain, false otherwise
     */
    boolean contains(int v);

    /**
     * Checks if the domain contains a single element.
     *
     * @return true if the domain contains a single element,
     *         false otherwise
     */
    boolean isSingleton();

    /**
     * Removes a value from the domain and notifies appropriately the listener.
     *
     * @param v the value to be removed
     * @param l the methods of the listener are notified as follows:
     *          <ul>
     *              <li> {@link IntDomainListener#change()} is called
     *              if v belongs to the domain</li>
     *              <li> {@link IntDomainListener#changeMax()} is called
     *              if v is equal to the maximum value</li>
     *              <li> {@link IntDomainListener#changeMin()} is called
     *              if v is equal to the minimum value</li>
     *              <li> {@link IntDomainListener#bind()} is called
     *              if v belongs to the domain and after its removal
     *                      the domain has a single value</li>
     *              <li> {@link IntDomainListener#empty()}  is called
     *              if v is the last value in the domain i.e.
     *              the domain is empty after this operation</li>
     *         </ul>
     */
    void remove(int v, IntDomainListener l);

    /**
     * Removes every value from the domain except the specified one.
     *
     * @param v the value to be kept
     * @param l the methods of the listener are notified as follows:
     *          <ul>
     *              <li> {@link IntDomainListener#change()} is called
     *              if some value is removed during the operation</li>
     *              <li> {@link IntDomainListener#changeMax()} is called
     *              if v is not equal to the maximum value</li>
     *              <li> {@link IntDomainListener#changeMin()} is called
     *              if v is not equal to the minimum value</li>
     *              <li> {@link IntDomainListener#bind()} is called
     *              if v belongs to the domain and after its removal
     *                      the domain has a single value</li>
     *              <li> {@link IntDomainListener#empty()}  is called
     *              if v is not in the domain i.e.
     *              the domain is empty after this operation</li>
     *         </ul>
     */
    void removeAllBut(int v, IntDomainListener l);

    /**
     * Removes every value less than the specified value from the domain.
     *
     * @param v the value such that all the values less than v are removed
     * @param l the methods of the listener are notified as follows:
     *          <ul>
     *              <li> {@link IntDomainListener#change()} is called
     *              if some value is removed during the operation</li>
     *              <li> {@link IntDomainListener#changeMax()} is called
     *              if v is is larger than the minimum value</li>
     *              <li> {@link IntDomainListener#bind()} is called
     *              if v is equal to the maximum value</li>
     *              <li> {@link IntDomainListener#empty()} is called
     *              if v is larger than the maximum value i.e.
     *              the domain is empty after this operation</li>
     *         </ul>
     */
    void removeBelow(int v, IntDomainListener l);

    /**
     * Removes every value larger than the specified value from the domain.
     *
     * @param v the value such that all the values larger than v are removed
     * @param l the methods of the listener are notified as follows:
     *          <ul>
     *              <li> {@link IntDomainListener#change()} is called
     *              if some value is removed during the operation</li>
     *              <li> {@link IntDomainListener#changeMax()} is called
     *              if v is is less than the maximum value</li>
     *              <li> {@link IntDomainListener#bind()} is called
     *              if v is equal to the minimum value</li>
     *              <li> {@link IntDomainListener#empty()} is called
     *              if v is less than the minimum value i.e.
     *              the domain is empty after this operation</li>
     *         </ul>
     */
    void removeAbove(int v, IntDomainListener l);

    /**
     * Copies the values of the domain into an array.
     *
     * @param dest an array large enough {@code dest.length >= size()}
     * @return the size of the domain and {@code dest[0,...,size-1]} contains
     *         the values in the domain in an arbitrary order
     */
    int fillArray(int[] dest);

    int fillDeltaArray(int oldMin, int oldMax, int oldSize, int [] arr);


    @Override
    String toString();
}
