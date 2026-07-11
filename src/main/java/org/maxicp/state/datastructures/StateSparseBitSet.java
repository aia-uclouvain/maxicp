/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */


package org.maxicp.state.datastructures;


import org.maxicp.state.State;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;

import java.util.Arrays;


/**
 * Class to represent a bit-set that can be saved and restored through
 * the {@link StateManager#saveState()} / {@link StateManager#restoreState()}
 */
public class StateSparseBitSet {

    /* Variables used to store value of the bitset */
    private int nWords;
    private State<Long>[] words;

    /* Variables used to make set sparse */
    private int[] nonZeroIdx;
    private StateInt nNonZero;

    private Long mask;

    /**
     * Bitset of the same capacity as the outer {@link StateSparseBitSet}.
     * It is not synchronized with  {@link StateManager}.
     * It is rather intended to be used as parameter to the
     * {@link #intersect(BitSet)} method to modify the outer {@link StateSparseBitSet}.
     */
    public class SupportBitSet extends BitSet {

        public SupportBitSet() {
            super(nWords);
        }

        public SupportBitSet(BitSet anotherBitSet) {
            this(anotherBitSet, false);
        }

        public SupportBitSet(BitSet anotherBitSet, boolean shared) {
            super(anotherBitSet, shared);
            assert anotherBitSet.nbWords == nWords : "BitSet size is incomptible with State Sparse BitSet size";
        }

    }

    /**
     * Temporary bitset used to hold temporary value.
     * Optimized to compute elements only on active words.
     */
    public class CollectionBitSet extends BitSet {

        public CollectionBitSet() { super(nWords); }

        @Override
        public void clear() {
            for (int i = nNonZero.value() - 1; i >= 0; i--) {
                this.words[nonZeroIdx[i]] = 0L;
            }
        }

        @Override
        public void union(BitSet other) {
            for (int i = nNonZero.value() - 1; i >= 0; i--) {
                int idx = nonZeroIdx[i];
                this.words[idx] |= other.words[idx];
            }

        }

        @Override
        public void intersect(BitSet other) {
            for (int i = nNonZero.value() - 1; i >= 0; i--) {
                int idx = nonZeroIdx[i];
                this.words[idx] &= other.words[idx];
            }
        }
    }

    /**
     * Creates a StateSparseSet with n bits, initially all set (value 1 for all)
     *
     * @param sm the state manager
     * @param n  the number of bits
     */
    public StateSparseBitSet(StateManager sm, int n) {
        nWords = (n + 63) >>> 6; // divided by 64
        words = new State[nWords];
        mask = ~0L >>> (64 - (n % 64));
        Arrays.setAll(words, i ->
                i == nWords - 1 ?
                        sm.makeStateLong(mask) :
                        sm.makeStateLong(0xFFFFFFFFFFFFFFFFL)
        );
        nonZeroIdx = new int[nWords];
        Arrays.setAll(nonZeroIdx, i -> i);
        nNonZero = sm.makeStateInt(nWords);
    }

    /**
     * Test is the reversibleSparseBitSet is empty
     *
     * @return true is empty, false otherwise
     */
    public boolean isEmpty() {
        return nNonZero.value() == 0;
    }

    /**
     * Remove the content of the BitSet from this sparseBitSet
     *
     * @param bs the BitSet to remove
     */
    public void remove(BitSet bs) {
        int size = nNonZero.value();
        for (int i = size - 1; i >= 0; i--) {
            int idx = nonZeroIdx[i];
            long remove = words[idx].value() & ~bs.words[idx];
            if (remove == 0L) {
                // deactivation of word
                size -= 1;
                nonZeroIdx[i] = nonZeroIdx[size];
                nonZeroIdx[size] = idx;
            } else {
                words[idx].setValue(remove);
            }
        }
        nNonZero.setValue(size);
    } //TODO check for mask at the end?

    /**
     * Intersect this sparseBitSet with bs
     *
     * @param bs the BitSet to intersect with
     */
    public void intersect(BitSet bs) {
        int size = nNonZero.value();
        for (int i = size - 1; i >= 0; i--) {
            int idx = nonZeroIdx[i];
            long intersect = words[idx].value() & bs.words[idx];
            if (intersect == 0L) {
                // deactivation of word
                size -= 1;
                nonZeroIdx[i] = nonZeroIdx[size];
                nonZeroIdx[size] = idx;
            } else {
                words[idx].setValue(intersect);
            }
        }
        nNonZero.setValue(size);
    }
    /**
     * Test the emptiness of the intersection with a given BitSet
     *
     * @param bs the BitSet to test the intersection with
     * @return true if empty, false otherwise
     */
    public boolean hasEmptyIntersection(BitSet bs) {
        for (int i = nNonZero.value() - 1; i >= 0; i--) {
            int idx = nonZeroIdx[i];
            if ((words[idx].value() & bs.words[idx]) != 0L) {
                return false;
            }
        }
        return true;
    }

    public int countIntersection(BitSet bs) {
        int count = 0;
        for (int i = nNonZero.value() - 1; i >= 0; i--) {
            int idx = nonZeroIdx[i];
            count += java.lang.Long.bitCount(words[idx].value() & bs.words[idx]);
        }
        return count;
    }

    /**
     * Counts the number of set bits.
     *
     * @return the number of set bits
     */
    public int count() {
        int count = 0;
        for (int i = nNonZero.value() - 1; i >= 0; i--) {
            count += java.lang.Long.bitCount(words[nonZeroIdx[i]].value());
        }
        return count;
    }

    /**
     * Counts the size of the intersection with a given BitSet,
     * stopping early as soon as the given cap is proved unreachable.
     * The returned value is exact when it is at least cap; otherwise it is
     * a value smaller than cap (the exact count is then also smaller than cap).
     *
     * @param bs  the BitSet to intersect with
     * @param cap the threshold of interest
     * @return the possibly capped size of the intersection
     */
    public int countIntersection(BitSet bs, int cap) {
        int count = 0;
        for (int i = nNonZero.value() - 1; i >= 0 && (i + 1) * 64 >= cap - count; i--) {
            int idx = nonZeroIdx[i];
            long word = bs.words[idx];
            if (word != 0L) {
                count += java.lang.Long.bitCount(words[idx].value() & word);
            }
        }
        return count;
    }

    /**
     * Tests if the set of bits is a subset of the bits of a given BitSet.
     *
     * @param bs the BitSet to test the inclusion in
     * @return true if every set bit is also set in bs
     */
    public boolean isSubsetOf(BitSet bs) {
        for (int i = nNonZero.value() - 1; i >= 0; i--) {
            int idx = nonZeroIdx[i];
            if ((words[idx].value() & ~bs.words[idx]) != 0L) {
                return false;
            }
        }
        return true;
    }

    /**
     * Counts the size of the intersection with several BitSets at once,
     * that is {@code |this ∩ sets[idx[0]] ∩ ... ∩ sets[idx[limit-1]]|}.
     *
     * @param sets  the BitSets to intersect with
     * @param idx   the indices in sets of the BitSets to consider
     * @param limit the number of indices to consider in idx
     * @return the size of the intersection of this with all designated BitSets
     */
    public int countIntersectionAll(BitSet[] sets, int[] idx, int limit) {
        int count = 0;
        for (int i = nNonZero.value() - 1; i >= 0; i--) {
            int offset = nonZeroIdx[i];
            long word = ~0L;
            for (int j = limit - 1; j >= 0 && word != 0L; j--) {
                word &= sets[idx[j]].words[offset];
            }
            if (word != 0L) {
                count += java.lang.Long.bitCount(words[offset].value() & word);
            }
        }
        return count;
    }

    @Override
    public String toString() {
        String res = "";
        for (int i = 0; i < nNonZero.value(); i++) {
            res += " w" + nonZeroIdx[i] + "=" + Long.toBinaryString(words[nonZeroIdx[i]].value());
        }
        return res;
    }
}
