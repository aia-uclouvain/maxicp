/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

public interface NodeListener {

    /**
     * Called whenever the node has been inserted
     */
    void insert();

    /**
     * Called whenever the node has been excluded
     */
    void exclude();

    /**
     * Called whenever an insertion for the node has been removed
     */
    void insertRemoved();

    /**
     * Called whenever the node has been required
     */
    void require();
}
