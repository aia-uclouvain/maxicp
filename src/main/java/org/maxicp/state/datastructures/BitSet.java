/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.state.datastructures;


import java.util.Arrays;

/**
 * Bitset abstract class
 */
public class BitSet {
    protected long[] words;
    protected int nbWords;

    /**
     * Initializes a bit-set.
     * All the bits are initially unset. The set it represents is thus empty.
     */
    public BitSet(int nWords) {
        words = new long[nWords];
        nbWords = nWords;
    }

    /**
     * Copy a bitset.
     * The bit set in anotherBitSet are also set here.
     *
     * @param anotherBitSet bitset to be copied
     */
    public BitSet(BitSet anotherBitSet) {
        this(anotherBitSet,false);
    }

    /**
     * Copy or share a bitset.
     * If the sharing is chosen, operations on one impacts the other as well
     *
     * @param anotherBitSet bitset to be copied/shared
     * @param shared true if the internal representation is meant to be shared between multiple bitsets
     */
    public BitSet(BitSet anotherBitSet, boolean shared) {
        words = shared ? anotherBitSet.words : anotherBitSet.words.clone();
        nbWords = anotherBitSet.nbWords;
    }

    /**
     * Set the ith bit up (value 1).
     *
     * @param i the bit to set
     */
    public void set(int i) {
        words[i >>> 6] |= 1L << i; // << is a cyclic shift, (1L << 64) == 1L
    }

    /**
     * Set the ith bit down (value 0).
     *
     * @param i the bit to set
     */
    public void unset(int i) {
        words[i >>> 6] &= ~(1L << i);
    }

    /**
     * Unset all the bits
     */
    public void clear() {
        Arrays.fill(words, 0L);
    }

    /**
     * Makes the union with another bit-set but
     * only on non zero-words of the outer sparse-bit-set.
     *
     * @param other the other bit-set to make the union with
     */
    public void union(BitSet other) {
        for (int i = 0; i < words.length; i++) {
            words[i] |= other.words[i];
        }
    }

    /**
     * Makes the intersection with another bit-set but
     * only on non zero-words of the outer sparse-bit-set.
     *
     * @param other the other bit-set to make the intersection with
     */
    public void intersect(BitSet other) {
        for (int i = 0; i < words.length; i++) {
            words[i] &= other.words[i];
        }
    }

    public void invert() {
        for (int i = 0; i < words.length; i++) {
            words[i] = ~words[i];
        }
    }

    @Override
    public String toString() {
        String res = "";
        for (int i = 0; i < nbWords; i++) {
            res += " w" + words[i] + "=" + Long.toBinaryString(words[i]);
        }
        return res;
    }
}
