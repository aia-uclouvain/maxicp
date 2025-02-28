/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.datastructures;


import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Tri-partition sparse-set data structure
 * that can be saved and restored through
 * the {@link org.maxicp.state.StateManager#saveState()} / {@link org.maxicp.state.StateManager#restoreState()}
 * methods.
 * The three partitions are the included (I), possible (P) and excluded (E) values.
 * Initially all the elements are in the possible set and those can only be
 * moved to the possible and excluded partitions.
 *
 *
 */
public class StateTriPartition {

    protected int[] elems;
    protected int[] elemPos;

    // +-----------+----------+----------+
    // |  included | possible | excluded |
    // +-----------+----------+----------+

    protected StateInt i;  // delimiter for the included values. They are included within 0...i-1
    protected StateInt p;  // delimiter for the possible values. They are included within i...p-1
    protected int n; // maximum number of elements

    protected int ofs; // offset
    protected int nOmitted; // number of values that are put in the exclusion set as soon as the instance was created

    /**
     * Creates a tri-partition with the elements {@code {I : {}, P: {0,...,n-1}, E: {}}}.
     *
     * @param sm the state manager that will save and restore the set when
     *        {@link org.maxicp.state.StateManager#saveState()} / {@link org.maxicp.state.StateManager#restoreState()}
     *           methods are called.
     * @param n number of elements within the set.
     */
    public StateTriPartition(StateManager sm, int n) {
        this(sm, 0, n-1);
    }

    /**
     * Creates a tri-partition with the elements {@code {I : {}, P: {min,...,max}, E: {}}}.
     *
     * @param sm the state manager that will save and restore the set when
     *        {@link StateManager#saveState()} / {@link StateManager#restoreState()}
     *           methods are called.
     * @param minInclusive minimum value of the partition
     * @param maxInclusive maximum value of the partition with {@code maxInclusive >= minInclusive}
     */
    public StateTriPartition(StateManager sm, int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) throw new IllegalArgumentException(minInclusive+"<"+maxInclusive);
        n = maxInclusive - minInclusive + 1;
        ofs = minInclusive;
        nOmitted = 0;
        i = sm.makeStateInt(0);
        p = sm.makeStateInt(n);
        elems = new int[n];
        elemPos = new int[n];
        for (int i = 0; i < n; i++) {
            elems[i] = i;
            elemPos[i] = i;
        }
    };

    /**
     * Creates a tri-partition with the elements {@code {R : {}, P: values, E: {}}}
     * @param sm the state manager that will save and restore the set when
     *        {@link StateManager#saveState()} / {@link StateManager#restoreState()}
     *           methods are called.
     * @param values the initial values for the possible partition P
     */
    public StateTriPartition(StateManager sm, Set<Integer> values) {
        this(sm, values.stream().min(Integer::compareTo).get(), values.stream().max(Integer::compareTo).get());
        for (int i = ofs; i < n + ofs ; ++i) {
            if (!values.contains(i)) {
                exclude(i);
                ++nOmitted;
            }
        }
    }

    /**
     * Moves a value from the set of possible values P to the set of excluded values E.
     *
     * @param val the value to move to the excluded set E
     * @return true if the value has been moved from the set of possible P to the set of excluded,
     *         false otherwise and the method has no effect in this case.
     */
    public boolean exclude(int val) {
        if (!isPossible(val))
            return false; // the value is already in the excluded set or in the set of included
        val -= ofs;
        this.p.decrement();
        exchangePositions(val, elems[p.value()]);
        return true;
    }

    /**
     * Moves a value from the possible partition P to the included partition I.
     *
     * @param val the value to mark as included
     * @return true if the value has been moved from the set of possible P to the set of included I,
     *         false otherwise and the method has no effect in this case.
     */
    public boolean include(int val) {
        if (!isPossible(val))
            return false; // the value is already in the excluded set or in the set of included
        val -= ofs;
        exchangePositions(val, elems[i.value()]);
        this.i.increment();
        return true;
    }

    /**
     * Sets the specified value as the only included one and move all others into the exclusion partition.
     *
     * @param v unique value that will be contained in the included partition.
     * @return true if the included partition was empty and the value was possible,
     *         false otherwise and the method has no effect.
     */
    public boolean includeAndExcludeOthers(int v) {
        if (!isPossible(v) || i.value() != 0)
            return false;
        // the value is set in the first position and the value for s and p are updated
        int val = elems[0];
        int index = elemPos[v];
        elemPos[v] = 0;
        elems[0] = v;
        elemPos[val] = index;
        elems[index] = val;
        i.setValue(1);
        p.setValue(1);
        return true;
    }


    /**
     * Exchanges the position of two values.
     *
     * @param val1 first value to exchange
     * @param val2 second value to exchange
     */
    private void exchangePositions(int val1, int val2) {
        assert (checkVal(val1));
        assert (checkVal(val2));
        int v1 = val1;
        int v2 = val2;
        int i1 = elemPos[v1];
        int i2 = elemPos[v2];
        elems[i1] = v2;
        elems[i2] = v1;
        elemPos[v1] = i2;
        elemPos[v2] = i1;
    }

    /**
     * @param val value to examine
     * @return true if the value belongs to the set of values
     */
    private boolean checkVal(int val) {
        assert (val < elems.length);
        return true;
    }

    /**
     * Moves all possible values into the set of excluded values.
     *
     * @return true if the partition of possible values has been reduced
     */
    public boolean excludeAllPossible() {
        if (p.value() == i.value())
            return false;
        this.p.setValue(i.value());
        return true;
    }

    /**
     * Moves all values, also the included ones into the set of excluded values.
     */
    public void excludeAll() {
        this.i.setValue(0);
        this.p.setValue(0);
    }

    /**
     * Moves all possible values into the set of included values.
     *
     * @return true if the partition of possible values has been reduced
     */
    public boolean includeAllPossible() {
        if (p.value() == i.value())
            return false;
        this.i.setValue(p.value());
        return true;
    }

    /**
     * Tells if the specified value belongs to the included partition I.
     *
     * @param val the value to test.
     * @return true if val belongs to the included partition I.
     */
    public boolean isIncluded(int val) {
        val -= ofs;
        if (val < 0 || val >= n)
            return false;
        else
            return elemPos[val] < i.value();
    }

    /**
     * Tells if the specified value belongs to the excluded partition E.
     *
     * @param val the value to test.
     * @return true if val belongs to the included partition I.
     */
    public boolean isExcluded(int val) {
        val -= ofs;
        if (val < 0 || val >= n)
            return false;
        else
            return elemPos[val] >= p.value() && elemPos[val] < n - nOmitted;
    }

    public boolean isPossible(int val) {
        val -= ofs;
        if (val < 0 || val >= n)
            return false;
        else
            return elemPos[val] < p.value() && elemPos[val] >= i.value();
    }

    /**
     * tell if a value belongs to the domain
     * @param val value to train1
     * @return true if the value is either included, possible or excluded
     */
    public boolean contains(int val) {
        val -= ofs;
        if (val < 0 || val >= n)
            return false;
        return elemPos[val] < n - nOmitted;
    }

    public int nPossible() {
        return p.value() - i.value();
    }

    public int nExcluded() {
        return n - p.value() - nOmitted;
    }

    public int nIncluded() {
        return i.value();
    }

    public int size() { return n - nOmitted;}

    public int fillIncluded(int[] dest) {
        int size = i.value();
        for (int i = 0; i < size ; ++i)
            dest[i] = elems[i] + ofs;
        return size;
    }

    /**
     * Sets the first values of <code>dest</code> to the included ones
     * that also satisfy the given filter predicate
     *
     * @param dest, an array large enough {@code dest.length >= size()}
     * @param filterPredicate the predicate, only elements for which the predicate is true are kept
     * @return the size of the included set of elements satisfying the predicate
     */
    public int fillIncluded(int[] dest, Predicate<Integer> filterPredicate) {
        int j = 0;
        int size = i.value();
        for (int i = 0; i < size ; ++i) {
            int v = elems[i] + ofs;
            if (filterPredicate.test(v)) {
                dest[j] = elems[i] + ofs;
                j++;
            }
        }
        return j;
    }

    public int fillPossible(int[] dest) {
        int begin = i.value();
        int end = p.value() - begin;
        if (ofs == 0) {
            System.arraycopy(elems, begin, dest, 0, end);
        } else {
            for (int i = 0; i < end ; i++) {
                dest[i] = elems[i + begin] + ofs;
            }
        }
        return end;
    }

    /**
     * Sets the first values of <code>dest</code> to the possible ones
     * that also satisfy the given filter predicate
     *
     * @param dest, an array large enough {@code dest.length >= size()}
     * @param filterPredicate the predicate, only elements for which the predicate is true are kept
     * @return the size of the possible set of elements satisfying the predicate
     */
    public int fillPossible(int[] dest, Predicate<Integer> filterPredicate) {
        int begin = i.value();
        int end = p.value() - begin;
        int j = 0;
        for (int i = 0; i < end; i++) {
            int v = elems[i + begin] + ofs;
            if (filterPredicate.test(v)) {
                dest[j] = v;
                j++;
            }
        }
        return j;
    }

    public int fillIncludedAndPossible(int[] dest) {
        int end = p.value();
        if (ofs == 0) {
            System.arraycopy(elems, 0, dest, 0, end);
        } else {
            for (int i = 0; i < end ; i++) {
                dest[i] = elems[i] + ofs;
            }
        }
        return end;
    }

    /**
     * Sets the first values of <code>dest</code> to the included and possible ones
     * that also satisfy the given filter predicate
     *
     * @param dest, an array large enough {@code dest.length >= size()}
     * @param filterPredicate the predicate, only elements for which the predicate is true are kept
     * @return the size of the included and possible set of elements satisfying the predicate
     */
    public int fillIncludedAndPossible(int[] dest, Predicate<Integer> filterPredicate) {
        int end = p.value();
        int j = 0;
        for (int i = 0; i < end ; i++) {
            int v = elems[i] + ofs;
            if (filterPredicate.test(v)) {
                dest[j] = elems[i] + ofs;
                j++;
            }
        }
        return j;
    }

    public int fillExcluded(int[] dest) {
        int begin = p.value();
        int end = n - begin - nOmitted;
        if (ofs == 0) {
            System.arraycopy(elems, begin, dest, 0, end);
        } else {
            for (int i = 0; i < end; i++) {
                dest[i] = elems[i + begin] + ofs;
            }
        }
        return end;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("I: {");

        int idx = 0;
        int i = this.i.value();
        int pVal = p.value();
        while (idx < i - 1) {
            b.append(elems[idx++] + ofs);
            b.append(',');
        }
        if (idx > 0)
            b.append(elems[idx++] + ofs);
        b.append("}\nP: {");

        while (idx < pVal - 1) {
            b.append(elems[idx++] + ofs);
            b.append(',');
        }
        if (pVal - idx > 0)
            b.append(elems[idx++] + ofs);
        b.append("}\nE: {");

        while (idx < n - 1 - nOmitted) {
            b.append(elems[idx++] + ofs);
            b.append(',');
        }
        if (nExcluded() > 0)
            b.append(elems[idx++] + ofs);
        b.append('}');
        return b.toString();
    }

}
