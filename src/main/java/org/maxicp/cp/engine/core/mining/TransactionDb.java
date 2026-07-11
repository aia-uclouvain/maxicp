/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core.mining;

import java.util.Arrays;

/**
 * Immutable transaction database for itemset mining.
 * <p>
 * Transactions are indexed {@code 0..nTrans()-1} and contain item identifiers.
 * Item identifiers follow the CP4IM/FIMI convention: they are positive integers,
 * so valid items range over {@code 1..nItems()-1} and index {@code 0} is unused
 * (its support is always 0).
 */
public class TransactionDb {

    private final String name;
    private final int[][] transactions;
    private final int nItems;
    private final int[] itemSupports;
    private final int[] labels;

    /**
     * Creates a transaction database.
     *
     * @param name         a name identifying the database (e.g. the file it was read from)
     * @param transactions the items of each transaction; each transaction is
     *                     normalized internally (sorted, duplicates removed)
     */
    public TransactionDb(String name, int[][] transactions) {
        this(name, transactions, null, 0);
    }

    /**
     * Creates a transaction database with one class label per transaction.
     *
     * @param name         a name identifying the database
     * @param transactions the items of each transaction
     * @param labels       the class label of each transaction, with
     *                     {@code labels.length == transactions.length}
     */
    public TransactionDb(String name, int[][] transactions, int[] labels) {
        this(name, transactions, labels, 0);
    }

    private TransactionDb(String name, int[][] transactions, int[] labels, int nItemsFloor) {
        if (labels != null && labels.length != transactions.length)
            throw new IllegalArgumentException("one label per transaction is required");
        this.name = name;
        this.labels = labels;
        this.transactions = new int[transactions.length][];
        int maxItem = nItemsFloor - 1;
        for (int t = 0; t < transactions.length; t++) {
            this.transactions[t] = normalize(transactions[t]);
            for (int item : this.transactions[t]) {
                if (item < 0)
                    throw new IllegalArgumentException("negative item " + item + " in transaction " + t);
                maxItem = Math.max(maxItem, item);
            }
        }
        this.nItems = maxItem + 1;
        this.itemSupports = new int[nItems];
        for (int[] transaction : this.transactions) {
            for (int item : transaction) {
                itemSupports[item]++;
            }
        }
    }

    private static int[] normalize(int[] items) {
        int[] sorted = items.clone();
        Arrays.sort(sorted);
        int n = 0;
        for (int k = 0; k < sorted.length; k++) {
            if (k == 0 || sorted[k] != sorted[k - 1]) {
                sorted[n++] = sorted[k];
            }
        }
        return n == sorted.length ? sorted : Arrays.copyOf(sorted, n);
    }

    /**
     * Returns the name of the database.
     *
     * @return the name of the database
     */
    public String name() {
        return name;
    }

    /**
     * Returns the number of item identifiers, that is the largest item identifier plus one.
     *
     * @return the number of item identifiers
     */
    public int nItems() {
        return nItems;
    }

    /**
     * Returns the number of transactions.
     *
     * @return the number of transactions
     */
    public int nTrans() {
        return transactions.length;
    }

    /**
     * Returns the items of a transaction, sorted increasingly and without duplicates.
     * The returned array must not be modified.
     *
     * @param t the transaction index with {@code 0 <= t < nTrans()}
     * @return the items of transaction t
     */
    public int[] transaction(int t) {
        return transactions[t];
    }

    /**
     * Returns the number of transactions containing an item.
     *
     * @param item the item with {@code 0 <= item < nItems()}
     * @return the support of the item
     */
    public int itemSupport(int item) {
        return itemSupports[item];
    }

    /**
     * Returns if the transactions carry class labels.
     *
     * @return true if the database is labeled
     */
    public boolean hasLabels() {
        return labels != null;
    }

    /**
     * Returns the class label of a transaction.
     *
     * @param t the transaction index with {@code 0 <= t < nTrans()}
     * @return the label of transaction t
     */
    public int label(int t) {
        if (labels == null)
            throw new IllegalStateException("the database is not labeled");
        return labels[t];
    }

    /**
     * Returns the sub-database of the transactions with a given label,
     * over the same item universe (same {@link #nItems()}).
     *
     * @param label the class label
     * @return the unlabeled sub-database of the transactions with this label
     */
    public TransactionDb subset(int label) {
        if (labels == null)
            throw new IllegalStateException("the database is not labeled");
        int count = 0;
        for (int l : labels) {
            if (l == label)
                count++;
        }
        int[][] selected = new int[count][];
        int k = 0;
        for (int t = 0; t < transactions.length; t++) {
            if (labels[t] == label)
                selected[k++] = transactions[t];
        }
        return new TransactionDb(name + ":" + label, selected, null, nItems);
    }

    @Override
    public String toString() {
        return name + " (nTrans=" + nTrans() + ", nItems=" + nItems() + ")";
    }
}
