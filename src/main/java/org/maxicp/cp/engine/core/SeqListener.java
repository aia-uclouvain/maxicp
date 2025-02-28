/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

public interface SeqListener {

    /**
     * Called whenever no possible node remains
     */
    void fix();

    /**
     * Called whenever a possible node has been inserted into the sequence
     */
    void insert();

    /**
     * Called whenever a possible node has been removed from the sequence
     */
    void exclude();

    /**
     * Called whenever a node has been set as required
     * If an insertion occurs {@link CPSeqVar#insert(int, int)}, both
     *      this method and {@link SeqListener#insert()} are triggered
     */
    void require();

    /**
     * Called whenever an insertion for a node is removed
     */
    void insertRemoved();

}
