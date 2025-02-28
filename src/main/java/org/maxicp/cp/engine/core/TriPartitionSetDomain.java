/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

import org.maxicp.state.StateManager;
import org.maxicp.state.datastructures.StateTriPartition;

public class TriPartitionSetDomain implements SetDomain {

    StateTriPartition triPartition;

    /**
     * Creates a tripartition domain with elements {0..n-1} possible
     * @param sm the state manager
     * @param n the size of the tripartition
     */
    public TriPartitionSetDomain(StateManager sm, int n) {
        triPartition = new StateTriPartition(sm,n);
    }

    @Override
    public void includeAll(SetDomainListener l) {
        if (triPartition.nPossible() > 0) {
            triPartition.includeAllPossible();
            l.include();
            l.change();
        }
    }

    @Override
    public void excludeAll(SetDomainListener l) {
        if (triPartition.nPossible() > 0) {
            triPartition.excludeAllPossible();
            l.exclude();
            l.change();
        }
    }

    @Override
    public void exclude(int v, SetDomainListener l) {
        if (triPartition.isIncluded(v)) {
            throw new RuntimeException(v+ "cannot be excluded, it is already included");
        }
        else if (triPartition.isPossible(v)) {
            triPartition.exclude(v);
            l.exclude();
            l.change();
        }
    }

    @Override
    public void include(int v, SetDomainListener l) {
        if (triPartition.isExcluded(v) || !triPartition.isPossible(v)) {
            throw new RuntimeException(v+ "cannot be included, it is already excluded");
        }
        if (triPartition.isPossible(v)) {
            triPartition.include(v);
            l.include();
            l.change();
        }
    }

    @Override
    public String toString() {
        return "";
    }

}
