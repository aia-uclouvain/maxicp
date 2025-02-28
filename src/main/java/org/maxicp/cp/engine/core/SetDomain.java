/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;

/**
 * Interface for set domain implementation.
 * A domain is encapsulated in an implementation.
 * A domain is like a set of integers.
 */
public interface SetDomain {

    void includeAll(SetDomainListener l);

    void excludeAll(SetDomainListener l);

    void exclude(int v, SetDomainListener l);

    void include(int v, SetDomainListener l);

    @Override
    String toString();
}
