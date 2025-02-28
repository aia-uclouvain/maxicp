/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine.core;


/**
 * SetDomain listeners are passed as argument
 * to the {@link SetDomain} modifier methods.
 */
public interface SetDomainListener {

    /**
     * Called whenever the set of possible is changed
     */
    void change();

    /**
     * Called whenever a possible value becomes included in the set
     */
    void include();

    /**
     * Called whenever a possible value becomes excluded from the set
     */
    void exclude();
}

