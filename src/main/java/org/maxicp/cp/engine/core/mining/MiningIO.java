/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core.mining;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Readers for pattern-mining databases.
 */
public final class MiningIO {

    private MiningIO() {
    }

    /**
     * Reads a transaction database in the CP4IM/FIMI sparse format:
     * one transaction per line, given as whitespace-separated positive item identifiers.
     * Blank lines are ignored.
     *
     * @param path the path of the file to read
     * @return the transaction database
     */
    public static TransactionDb readTransactionDb(String path) {
        List<int[]> transactions = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(Path.of(path))) {
                String trimmed = line.trim();
                if (trimmed.isEmpty())
                    continue;
                String[] tokens = trimmed.split("\\s+");
                int[] items = new int[tokens.length];
                for (int k = 0; k < tokens.length; k++) {
                    items[k] = Integer.parseInt(tokens[k]);
                }
                transactions.add(items);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new TransactionDb(path, transactions.toArray(new int[0][]));
    }

    /**
     * Reads a labeled transaction database: same sparse format as
     * {@link #readTransactionDb(String)} but the last integer of each line is
     * the class label of the transaction.
     *
     * @param path the path of the file to read
     * @return the labeled transaction database
     */
    public static TransactionDb readLabeledTransactionDb(String path) {
        List<int[]> transactions = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(Path.of(path))) {
                String trimmed = line.trim();
                if (trimmed.isEmpty())
                    continue;
                String[] tokens = trimmed.split("\\s+");
                int[] items = new int[tokens.length - 1];
                for (int k = 0; k < tokens.length - 1; k++) {
                    items[k] = Integer.parseInt(tokens[k]);
                }
                transactions.add(items);
                labels.add(Integer.parseInt(tokens[tokens.length - 1]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new TransactionDb(path, transactions.toArray(new int[0][]),
                labels.stream().mapToInt(Integer::intValue).toArray());
    }
}
