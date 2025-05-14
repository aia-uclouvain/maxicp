/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.datastructures;

import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;
import org.maxicp.util.exception.InconsistencyException;

import java.security.InvalidParameterException;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Set implemented using a sparse-set data structure
 * that can be saved and restored through
 * the {@link StateManager#saveState()} / {@link StateManager#restoreState()}
 * methods.
 */
public class StateSparseSet {

    private int[] values;
    private int[] indexes;
    private StateInt size;
    private StateInt min;
    private StateInt max;
    private int ofs;
    private int n;

    /**
     * Creates a set containing the elements {@code {ofs,ofs+1,...,ofs+n-1}}.
     *
     * @param sm the state manager that will save and restore the set when
     *        {@link StateManager#saveState()} / {@link StateManager#restoreState()}
     *           mehtods are called
     * @param n  the number of elements in the set
     * @param ofs the minimum value in the set containing {@code {ofs,ofs+1,...,ofs+n-1}}
     */
    public StateSparseSet(StateManager sm, int n, int ofs) {
        this.n = n;
        this.ofs = ofs;
        size = sm.makeStateInt(n);
        min = sm.makeStateInt(0);
        max = sm.makeStateInt(n - 1);
        values = new int[n];
        indexes = new int[n];
        for (int i = 0; i < n; i++) {
            values[i] = i;
            indexes[i] = i;
        }
    }

    /**
     * Creates a set containing the elements of  {@code values}.
     *
     * @param sm     the state manager that will save and restore the set when
     *               {@link StateManager#saveState()} / {@link StateManager#restoreState()}
     *               mehtods are called
     * @param values the elements to initialize the set with
     */
    public StateSparseSet(StateManager sm, Set<Integer> values) {
        this(sm, values.stream().max(Integer::compare).get() - values.stream().min(Integer::compare).get() + 1, values.stream().min(Integer::compare).get());
        if (values.isEmpty()) throw new InvalidParameterException("at least one setValue in the domain");
        for (int i = min(); i < max(); i++) {
            if (!values.contains(i)) {
                try {
                    this.remove(i);
                } catch (InconsistencyException e) {
                }
            }
        }
    }

    /**
     * The initial minimum value of the sparse-set (at its creation)
     * @return the initial minimum value of the sparse-set
     */
    public int initMin() {
        return ofs;
    }

    /**
     * The initial maximum value of the sparse-set (at its creation)
     * @return the initial maximum value of the sparse-set
     */
    public int initMax() {
        return ofs + n - 1;
    }

    /**
     * The initial size of the sparse-set (at its creation)
     * @return
     */
    public int initSize() {
        return n;
    }


    private void exchangePositions(int val1, int val2) {
        assert (checkVal(val1));
        assert (checkVal(val2));
        int v1 = val1;
        int v2 = val2;
        int i1 = indexes[v1];
        int i2 = indexes[v2];
        values[i1] = v2;
        values[i2] = v1;
        indexes[v1] = i2;
        indexes[v2] = i1;
    }

    private boolean checkVal(int val) {
        assert (val <= values.length - 1);
        return true;
    }

    /**
     * Returns an array with the values present in the set.
     *
     * @return an array representation of the values present in the set
     */
    public int[] toArray() {
        int[] res = new int[size()];
        fillArray(res);
        return res;
    }

    /**
     * Sets the first values of <code>dest</code> to the ones
     * present in the set.
     *
     * @param dest, an array large enough {@code dest.length >= size()}
     * @return the size of the set
     */
    public int fillArray(int[] dest) {
        int s = size.value();
        for (int i = 0; i < s; i++)
            dest[i] = values[i] + ofs;
        return s;
    }

    /**
     * Sets the first values of <code>dest</code> to the ones
     * present in the set.
     *
     * @param dest, an array large enough {@code dest.length >= n - size()}
     * @return the size of the set
     */
    public int fillExcluded(int[] dest) {
        int s = size.value();
        for (int i = s; i < n; i++)
            dest[i-s] = values[i] + ofs;
        return n-s;
    }

    /**
     * Sets the first values of <code>dest</code> to the ones
     * present in the set that also satisfy the given filter predicate
     *
     * @param dest, an array large enough {@code dest.length >= size()}
     * @param filterPredicate the predicate, only elements for which the predicate is true are kept
     * @return the size of the set of elements in the set satisfying hte predicate
     */
    public int fillArrayWithFilter(int[] dest, Predicate<Integer> filterPredicate) {
        int s = size.value();
        int j = 0;
        for (int i = 0; i < s; i++) {
            if (filterPredicate.test(values[i]+ofs)) {
                dest[j] = values[i] + ofs;
                j++;
            }
        }
        return j;
    }



    /**
     * Checks if the set is empty
     *
     * @return true if the set is empty
     */
    public boolean isEmpty() {
        return size.value() == 0;
    }

    /**
     * Returns the size of the set.
     *
     * @return the size of the set
     */
    public int size() {
        return size.value();
    }

    /**
     * Returns the minimum value in the set.
     *
     * @return the minimum value in the set
     */
    public int min() {
        if (isEmpty())
            throw new NoSuchElementException();
        return min.value() + ofs;
    }

    /**
     * Returns the maximum value in the set.
     *
     * @return the maximum value in the set
     */
    public int max() {
        if (isEmpty())
            throw new NoSuchElementException();
        else return max.value() + ofs;
    }

    private void updateBoundsValRemoved(int val) {
        updateMaxValRemoved(val);
        updateMinValRemoved(val);
    }

    private void updateMaxValRemoved(int val) {
        if (!isEmpty() && max.value() == val) {
            assert (!internalContains(val));
            //the maximum was removed, search the new one
            for (int v = val - 1; v >= min.value(); v--) {
                if (internalContains(v)) {
                    max.setValue(v);
                    return;
                }
            }
        }
    }

    private void updateMinValRemoved(int val) {
        if (!isEmpty() && min.value() == val) {
            assert (!internalContains(val));
            //the minimum was removed, search the new one
            for (int v = val + 1; v <= max.value(); v++) {
                if (internalContains(v)) {
                    min.setValue(v);
                    return;
                }
            }
        }
    }

    /**
     * Removes the given value from the set.
     *
     * @param val the value to remove.
     * @return true if val was in the set, false otherwise
     */
    public boolean remove(int val) {
        if (!contains(val))
            return false; //the setValue has already been removed
        val -= ofs;
        assert (checkVal(val));
        int s = size();
        exchangePositions(val, values[s - 1]);
        size.decrement();
        updateBoundsValRemoved(val);
        return true;
    }

    /**
     * Removes the value for which a given predicate is true
     *
     * @param filter the predicate, only elements for which the predicate is true are removed
     * @return the size of the set after removal
     */
    public int remove(Predicate<Integer> filter) {
        int s = size();
        for (int i = 0 ; i < s ; ++i) {
            int v = values[i] + ofs;
            if (filter.test(v)) {
                remove(v);
                --s; // size is reduced
                --i; // values[i] has been swapped and needs to be reevaluated
            }
        }
        return s;
    }

    /**
     * This method operates on the shifted value (one cannot shift now).
     *
     * @param val the setValue to lookup for membership
     * @return true if val is in the set, false otherwise
     */
    private boolean internalContains(int val) {
        if (val < 0 || val >= n)
            return false;
        else
            return indexes[val] < size();
    }

    /**
     * Checks if a value is in the set.
     *
     * @param val the value to check
     * @return true if val is in the set
     */
    public boolean contains(int val) {
        val -= ofs;
        if (val < 0 || val >= n)
            return false;
        else
            return indexes[val] < size();
    }

    /**
     * Removes all the element from the set except the given value.
     *
     * @param v is an element in the set
     */
    public void removeAllBut(int v) {
        // we only have to put in first position this setValue and set the size to 1
        assert (contains(v));
        v -= ofs;
        assert (checkVal(v));
        int val = values[0];
        int index = indexes[v];
        indexes[v] = 0;
        values[0] = v;
        indexes[val] = index;
        values[index] = val;
        min.setValue(v);
        max.setValue(v);
        size.setValue(1);
    }

    /**
     * Removes all the values in the set.
     */
    public void removeAll() {
        size.setValue(0);
    }

    /**
     * Remove all the values less than the given value from the set
     *
     * @param value a value such that all the ones smaller are removed
     */
    public void removeBelow(int value) {
        if (max() < value) {
            removeAll();
        } else {
            for (int v = min(); v < value; v++) {
                remove(v);
            }
        }
    }

    /**
     * Remove all the values larger than the given value from the set
     *
     * @param value a value such that all the ones greater are removed
     */
    public void removeAbove(int value) {
        if (min() > value) {
            removeAll();
        } else {
            int max = max();
            for (int v = max; v > value; v--) {
                remove(v);
            }
        }
    }


    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("{");
        for (int i = 0; i < size() - 1; i++) {
            b.append(values[i] + ofs);
            b.append(',');
        }
        if (size() > 0) b.append(values[size() - 1] + ofs);
        b.append("}");
        return b.toString();
    }

    public int fillDeltaArray(int oldMin, int oldMax, int oldSize, int [] arr) {
        assert(oldMin >= ofs && oldMax < n+ofs);
        int currSize = size();
        for (int i = 0; i < oldSize-currSize ; i++) {
            arr[i] = values[currSize+i]+ofs;
        }
        return oldSize-currSize;
    }


}
