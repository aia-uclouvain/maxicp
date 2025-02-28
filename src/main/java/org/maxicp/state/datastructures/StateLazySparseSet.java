/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.datastructures;

import org.maxicp.state.State;
import org.maxicp.state.StateManager;

/**
 * A sparse-set that lazily switch
 * from a dense interval representation
 * to a sparse-set representation
 * when a hole is created in the interval.
 */
public class StateLazySparseSet {

    private StateManager sm;

    private StateSparseSet sparse;
    private StateInterval interval;


    private State<Boolean> intervalRep;
    private boolean switched = false;

    private boolean isInterval() {
        return intervalRep.value();
    }

    /**
     * Creates a set containing the elements {@code {ofs,ofs+1,...,ofs+n-1}}.
     *
     * @param sm the state manager that will save and restore the set when
     *        {@link StateManager#saveState()} / {@link StateManager#restoreState()}
     *           mehtods are called
     * @param n  the number of elements in the set
     * @param ofs the minimum value in the set containing {@code {ofs,ofs+1,...,ofs+n-1}}
     */
    public StateLazySparseSet(StateManager sm, int n, int ofs) {
        this.sm = sm;
        interval = new StateInterval(sm, ofs, ofs + n - 1);
        intervalRep = sm.makeStateRef(true);

        // optimization to avoid trashing with the creation of sparse rep
        sm.onRestore(() -> {
            if (switched && isInterval()) buildSparse();
        });
    }

    private void buildSparse() {
        sparse = new StateSparseSet(sm, max() - min() + 1, min());
        intervalRep.setValue(false);
        switched = true;
    }

    /**
     * @return true if the set is empty
     */
    public boolean isEmpty() {
        return isInterval() ? interval.isEmpty() : sparse.isEmpty();
    }

    /**
     * @return the size of the set
     */
    public int size() {
        return isInterval() ? interval.size() : sparse.size();
    }

    /**
     * Returns the minimum value in the set.
     *
     * @return the minimum value in the set
     */
    public int min() {
        if (isInterval()) {
            return interval.min();
        } else {
            return sparse.min();
        }
    }

    /**
     * Returns the maximum value in the set.
     *
     * @return the maximum value in the set
     */
    public int max() {
        if (isInterval()) {
            return interval.max();
        } else {
            return sparse.max();
        }
    }

    /**
     * Checks if a value is in the set.
     *
     * @param val the value to check
     * @return true if val is in the set
     */
    public boolean contains(int val) {
        if (isInterval()) {
            return interval.contains(val);
        } else {
            return sparse.contains(val);
        }
    }

    /**
     * Sets the first values of <code>dest</code> to the ones
     * present in the set.
     *
     * @param dest, an array large enough {@code dest.length >= size()}
     * @return the size of the set
     */
    public int fillArray(int[] dest) {
        if (isInterval()) {
            int s = size();
            int from = min();
            for (int i = 0; i < s; i++)
                dest[i] = from + i;
            return s;
        } else return sparse.fillArray(dest);
    }

    /**
     * Removes the given value from the set.
     *
     * @param val the value to remove.
     * @return true if val was in the set, false otherwise
     */
    public boolean remove(int val) {
        if (isInterval()) {
            if (!interval.contains(val)) {
                return false;
            } else if (val == interval.min()) {
                interval.removeBelow(val + 1);
                return true;
            } else if (val == interval.max()) {
                interval.removeAbove(val - 1);
                return true;
            } else {
                buildSparse();
                return sparse.remove(val);
            }
        } else return sparse.remove(val);
    }

    /**
     * Removes all the element from the set except the given value.
     *
     * @param v is an element in the set
     */
    public void removeAllBut(int v) {
        if (isInterval()) {
            interval.removeAllBut(v);
        } else {
            sparse.removeAllBut(v);
        }
    }

    /**
     * Removes all the values in the set.
     */
    public void removeAll() {
        if (isInterval()) {
            interval.removeAll();
        } else {
            sparse.removeAll();
        }
    }

    /**
     * Remove all the values less than the given value from the set
     *
     * @param value a value such that all the ones smaller are removed
     */
    public void removeBelow(int value) {
        if (isInterval()) {
            interval.removeBelow(value);
        } else {
            sparse.removeBelow(value);
        }
    }

    /**
     * Remove all the values larger than the given value from the set
     *
     * @param value a value such that all the ones greater are removed
     */
    public void removeAbove(int value) {
        if (isInterval()) {
            interval.removeAbove(value);
        } else {
            sparse.removeAbove(value);
        }
    }

    @Override
    public String toString() {
        if (isInterval()) {
            return interval.toString();
        } else {
            return sparse.toString();
        }
    }

    public int fillDeltaArray(int oldMin, int oldMax, int oldSize, int [] arr) {
        if (switched) {

            // must be cautious here because the bounds might have changed before the sparse-set creation
            int oldMinSparseSet = Math.max(sparse.initMin(),oldMin);
            int oldMaxSparseSet = Math.min(sparse.initMax(),oldMax);
            int oldSizeSparseSet = Math.min(sparse.initSize(),oldSize);

            int s =  sparse.fillDeltaArray(oldMinSparseSet,oldMaxSparseSet,oldSizeSparseSet,arr);
            for (int v = oldMin; v < oldMinSparseSet; v++) {
                arr[s++] = v;
            }
            for (int v = oldMaxSparseSet + 1; v <= oldMax; v++) {
                arr[s++] = v;
            }
            return s;
        } else {
            int i = 0;
            for (int v = oldMin; v < min(); v++) {
                arr[i++] = v;
            }
            for (int v = max() + 1; v <= oldMax; v++) {
                arr[i++] = v;
            }
            assert(i == oldSize - size());
            return i;
        }
    }


}
