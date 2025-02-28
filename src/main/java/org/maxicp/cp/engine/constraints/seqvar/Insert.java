/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.constraints.seqvar;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPSeqVar;

/**
 * Inserts a node into a sequence
 */
public class Insert extends AbstractCPConstraint {

    private final CPSeqVar seqVar;
    private final int prev;
    private final int node;

    /**
     * Inserts a node into a sequence, after a given insertion.
     * This extends the sequence, such that a link ... -> prev -> node -> ... is formed.
     *
     * @param seqVar sequence where the node needs to be inserted
     * @param prev predecessor for the node
     * @param node node to insert
     */
    public Insert(CPSeqVar seqVar, int prev, int node) {
        super(seqVar.getSolver());
        this.seqVar = seqVar;
        this.prev = prev;
        this.node = node;
    }

    @Override
    public void post() {
        seqVar.insert(prev, node);
    }

}
